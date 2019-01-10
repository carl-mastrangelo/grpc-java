/*
 * Copyright 2014 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.netty;

import static io.grpc.internal.GrpcUtil.KEEPALIVE_TIME_NANOS_DISABLED;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.InternalLogId;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.ClientStream;
import io.grpc.internal.ConnectionClientTransport;
import io.grpc.internal.FailingClientStream;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.Http2Ping;
import io.grpc.internal.KeepAliveManager;
import io.grpc.internal.KeepAliveManager.ClientKeepAlivePinger;
import io.grpc.internal.StatsTraceContext;
import io.grpc.internal.TransportTracer;
import io.grpc.netty.NettyChannelBuilder.LocalSocketPicker;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2ChannelClosedException;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/**
 * A Netty-based {@link ConnectionClientTransport} implementation.
 */
class NettyClientTransport implements ConnectionClientTransport {
  private final InternalLogId logId;
  private final Map<ChannelOption<?>, ?> channelOptions;
  private final SocketAddress remoteAddress;
  private final Class<? extends Channel> channelType;
  private final EventLoopGroup group;
  private final ProtocolNegotiator negotiator;
  private final String authorityString;
  private final AsciiString authority;
  private final AsciiString userAgent;
  private final int flowControlWindow;
  private final int maxMessageSize;
  private final int maxHeaderListSize;
  private KeepAliveManager keepAliveManager;
  private final long keepAliveTimeNanos;
  private final long keepAliveTimeoutNanos;
  private final boolean keepAliveWithoutCalls;
  private final Runnable tooManyPingsRunnable;
  private ProtocolNegotiator.Handler negotiationHandler;
  private NettyClientHandler handler;
  // We should not send on the channel until negotiation completes. This is a hard requirement
  // by SslHandler but is appropriate for HTTP/1.1 Upgrade as well.
  private Channel channel;
  /** If {@link #start} has been called, non-{@code null} if channel is {@code null}. */
  private Status statusExplainingWhyTheChannelIsNull;
  /** Since not thread-safe, may only be used from event loop. */
  private ClientTransportLifecycleManager lifecycleManager;
  /** Since not thread-safe, may only be used from event loop. */
  private final TransportTracer transportTracer;
  private final Attributes eagAttributes;
  private final LocalSocketPicker localSocketPicker;

  NettyClientTransport(
      SocketAddress address, Class<? extends Channel> channelType,
      Map<ChannelOption<?>, ?> channelOptions, EventLoopGroup group,
      ProtocolNegotiator negotiator, int flowControlWindow, int maxMessageSize,
      int maxHeaderListSize, long keepAliveTimeNanos, long keepAliveTimeoutNanos,
      boolean keepAliveWithoutCalls, String authority, @Nullable String userAgent,
      Runnable tooManyPingsRunnable, TransportTracer transportTracer, Attributes eagAttributes,
      LocalSocketPicker localSocketPicker) {
    this.negotiator = Preconditions.checkNotNull(negotiator, "negotiator");
    this.remoteAddress = Preconditions.checkNotNull(address, "address");
    this.group = Preconditions.checkNotNull(group, "group");
    this.channelType = Preconditions.checkNotNull(channelType, "channelType");
    this.channelOptions = Preconditions.checkNotNull(channelOptions, "channelOptions");
    this.flowControlWindow = flowControlWindow;
    this.maxMessageSize = maxMessageSize;
    this.maxHeaderListSize = maxHeaderListSize;
    this.keepAliveTimeNanos = keepAliveTimeNanos;
    this.keepAliveTimeoutNanos = keepAliveTimeoutNanos;
    this.keepAliveWithoutCalls = keepAliveWithoutCalls;
    this.authorityString = authority;
    this.authority = new AsciiString(authority);
    this.userAgent = new AsciiString(GrpcUtil.getGrpcUserAgent("netty", userAgent));
    this.tooManyPingsRunnable =
        Preconditions.checkNotNull(tooManyPingsRunnable, "tooManyPingsRunnable");
    this.transportTracer = Preconditions.checkNotNull(transportTracer, "transportTracer");
    this.eagAttributes = Preconditions.checkNotNull(eagAttributes, "eagAttributes");
    this.localSocketPicker = Preconditions.checkNotNull(localSocketPicker, "localSocketPicker");
    this.logId = InternalLogId.allocate(getClass(), remoteAddress.toString());
  }

  @Override
  public void ping(final PingCallback callback, final Executor executor) {
    if (channel == null) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          callback.onFailure(statusExplainingWhyTheChannelIsNull.asException());
        }
      });
      return;
    }
    // The promise and listener always succeed in NettyClientHandler. So this listener handles the
    // error case, when the channel is closed and the NettyClientHandler no longer in the pipeline.
    ChannelFutureListener failureListener = new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
          Status s = statusFromFailedFuture(future);
          Http2Ping.notifyFailed(callback, executor, s.asException());
        }
      }
    };
    // Write the command requesting the ping
    handler.getWriteQueue().enqueue(new SendPingCommand(callback, executor), true)
        .addListener(failureListener);
  }

  @Override
  public ClientStream newStream(
      MethodDescriptor<?, ?> method, Metadata headers, CallOptions callOptions) {
    Preconditions.checkNotNull(method, "method");
    Preconditions.checkNotNull(headers, "headers");
    if (channel == null) {
      return new FailingClientStream(statusExplainingWhyTheChannelIsNull);
    }
    StatsTraceContext statsTraceCtx = StatsTraceContext.newClientContext(callOptions, headers);
    return new NettyClientStream(
        new NettyClientStream.TransportState(
            handler,
            channel.eventLoop(),
            maxMessageSize,
            statsTraceCtx,
            transportTracer) {
          @Override
          protected Status statusFromFailedFuture(ChannelFuture f) {
            return NettyClientTransport.this.statusFromFailedFuture(f);
          }
        },
        method,
        headers,
        channel,
        authority,
        negotiationHandler.scheme(),
        userAgent,
        statsTraceCtx,
        transportTracer,
        callOptions);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Runnable start(Listener transportListener) {
    lifecycleManager = new ClientTransportLifecycleManager(
        Preconditions.checkNotNull(transportListener, "listener"));
    EventLoop eventLoop = group.next();
    if (keepAliveTimeNanos != KEEPALIVE_TIME_NANOS_DISABLED) {
      keepAliveManager = new KeepAliveManager(
          new ClientKeepAlivePinger(this), eventLoop, keepAliveTimeNanos, keepAliveTimeoutNanos,
          keepAliveWithoutCalls);
    }

    handler = NettyClientHandler.newHandler(
        lifecycleManager,
        keepAliveManager,
        flowControlWindow,
        maxHeaderListSize,
        GrpcUtil.STOPWATCH_SUPPLIER,
        tooManyPingsRunnable,
        transportTracer,
        eagAttributes,
        authorityString);
    NettyHandlerSettings.setAutoWindow(handler);

    negotiationHandler = negotiator.newHandler(handler);

    Bootstrap b = new Bootstrap();
    b.group(eventLoop);
    b.channel(channelType);
    if (NioSocketChannel.class.isAssignableFrom(channelType)) {
      b.option(SO_KEEPALIVE, true);
    }
    for (Map.Entry<ChannelOption<?>, ?> entry : channelOptions.entrySet()) {
      // Every entry in the map is obtained from
      // NettyChannelBuilder#withOption(ChannelOption<T> option, T value)
      // so it is safe to pass the key-value pair to b.option().
      b.option((ChannelOption<Object>) entry.getKey(), entry.getValue());
    }

    ChannelHandler bufferingHandler =
        new WriteBufferingAndExceptionHandler(lifecycleManager, negotiationHandler);

    /**
     * We don't use a ChannelInitializer in the client bootstrap because its "initChannel" method
     * is executed in the event loop and we need this handler to be in the pipeline immediately so
     * that it may begin buffering writes.
     */
    b.handler(bufferingHandler);
    ChannelFuture regFuture = b.register();
    if (regFuture.isDone() && !regFuture.isSuccess()) {
      channel = null;
      // Initialization has failed badly. All new streams should be made to fail.
      Throwable t = regFuture.cause();
      if (t == null) {
        t = new IllegalStateException("Channel is null, but future doesn't have a cause");
      }
      statusExplainingWhyTheChannelIsNull = Utils.statusFromThrowable(t);
      // Use a Runnable since lifecycleManager calls transportListener
      return new Runnable() {
        @Override
        public void run() {
          // NOTICE: we not are calling lifecycleManager from the event loop. But there isn't really
          // an event loop in this case, so nothing should be accessing the lifecycleManager. We
          // could use GlobalEventExecutor (which is what regFuture would use for notifying
          // listeners in this case), but avoiding on-demand thread creation in an error case seems
          // a good idea and is probably clearer threading.
          lifecycleManager.notifyTerminated(statusExplainingWhyTheChannelIsNull);
        }
      };
    }
    channel = regFuture.channel();
    // Start the write queue as soon as the channel is constructed
    handler.startWriteQueue(channel);
    // This write will have no effect, yet it will only complete once the negotiationHandler
    // flushes any pending writes. We need it to be staged *before* the `connect` so that
    // the channel can't have been closed yet, removing all handlers. This write will sit in the
    // AbstractBufferingHandler's buffer, and will either be flushed on a successful connection,
    // or failed if the connection fails.
    channel.writeAndFlush(NettyClientHandler.NOOP_MESSAGE).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
          // Need to notify of this failure, because NettyClientHandler may not have been added to
          // the pipeline before the error occurred.
          //lifecycleManager.notifyTerminated(Utils.statusFromThrowable(future.cause()));
        }
      }
    });
    // Start the connection operation to the server.
    SocketAddress localAddress =
        localSocketPicker.createSocketAddress(remoteAddress, eagAttributes);
    if (localAddress != null) {
      channel.connect(remoteAddress, localAddress);
    } else {
      channel.connect(remoteAddress);
    }

    if (keepAliveManager != null) {
      keepAliveManager.onTransportStarted();
    }

    return null;
  }

  @Override
  public void shutdown(Status reason) {
    // start() could have failed
    if (channel == null) {
      return;
    }
    // Notifying of termination is automatically done when the channel closes.
    if (channel.isOpen()) {
      handler.getWriteQueue().enqueue(new GracefulCloseCommand(reason), true);
    }
  }

  @Override
  public void shutdownNow(final Status reason) {
    // Notifying of termination is automatically done when the channel closes.
    if (channel != null && channel.isOpen()) {
      handler.getWriteQueue().enqueue(new Runnable() {
        @Override
        public void run() {
          lifecycleManager.notifyShutdown(reason);
          // Call close() directly since negotiation may not have completed, such that a write would
          // be queued.
          channel.close();
          channel.write(new ForcefulCloseCommand(reason));
        }
      }, true);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("logId", logId.getId())
        .add("remoteAddress", remoteAddress)
        .add("channel", channel)
        .toString();
  }

  @Override
  public InternalLogId getLogId() {
    return logId;
  }

  @Override
  public Attributes getAttributes() {
    return handler.getAttributes();
  }

  @Override
  public ListenableFuture<SocketStats> getStats() {
    final SettableFuture<SocketStats> result = SettableFuture.create();
    if (channel.eventLoop().inEventLoop()) {
      // This is necessary, otherwise we will block forever if we get the future from inside
      // the event loop.
      result.set(getStatsHelper(channel));
      return result;
    }
    channel.eventLoop().submit(
        new Runnable() {
          @Override
          public void run() {
            result.set(getStatsHelper(channel));
          }
        })
        .addListener(
            new GenericFutureListener<Future<Object>>() {
              @Override
              public void operationComplete(Future<Object> future) throws Exception {
                if (!future.isSuccess()) {
                  result.setException(future.cause());
                }
              }
            });
    return result;
  }

  private SocketStats getStatsHelper(Channel ch) {
    assert ch.eventLoop().inEventLoop();
    return new SocketStats(
        transportTracer.getStats(),
        channel.localAddress(),
        channel.remoteAddress(),
        Utils.getSocketOptions(ch),
        handler == null ? null : handler.getSecurityInfo());
  }

  @VisibleForTesting
  Channel channel() {
    return channel;
  }

  @VisibleForTesting
  KeepAliveManager keepAliveManager() {
    return keepAliveManager;
  }

  /**
   * Convert ChannelFuture.cause() to a Status, taking into account that all handlers are removed
   * from the pipeline when the channel is closed. Since handlers are removed, you may get an
   * unhelpful exception like ClosedChannelException.
   *
   * <p>This method must only be called on the event loop.
   */
  private Status statusFromFailedFuture(ChannelFuture f) {
    Throwable t = f.cause();
    if (t instanceof ClosedChannelException
        // Exception thrown by the StreamBufferingEncoder if the channel is closed while there
        // are still streams buffered. This exception is not helpful. Replace it by the real
        // cause of the shutdown (if available).
        || t instanceof Http2ChannelClosedException) {
      Status shutdownStatus = lifecycleManager.getShutdownStatus();
      if (shutdownStatus == null) {
        return Status.UNKNOWN.withDescription("Channel closed but for unknown reason")
            .withCause(new ClosedChannelException().initCause(t));
      }
      return shutdownStatus;
    }
    return Utils.statusFromThrowable(t);
  }

  /**
   * Buffers all writes until either {@link #writeBufferedAndRemove(ChannelHandlerContext)} or
   * {@link #fail(ChannelHandlerContext, Throwable)} is called. This handler allows us to
   * write to a {@link io.netty.channel.Channel} before we are allowed to write to it officially
   * i.e.  before it's active or the TLS Handshake is complete.
   */
  static final class WriteBufferingAndExceptionHandler extends ChannelDuplexHandler {
    private final Queue<ChannelWrite> bufferedWrites = new ArrayDeque<>();
    private final ChannelHandler next;
    private boolean writing;
    private boolean flushRequested;
    private Throwable failCause;

    private final ClientTransportLifecycleManager lifecycleManager;

    WriteBufferingAndExceptionHandler(
        ClientTransportLifecycleManager lifecycleManager, ChannelHandler next) {
      this.lifecycleManager = lifecycleManager;
      this.next = next;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      ctx.pipeline().addBefore(ctx.name(), null, next);
      super.handlerAdded(ctx);
    }

    /**
     * If this channel becomes inactive, then notify all buffered writes that we failed.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      Status status = Status.UNAVAILABLE
          .withDescription("Connection broken while performing protocol negotiation ("
              + ctx.pipeline().first() + ')');
      failWrites(status.asRuntimeException());
      lifecycleManager.notifyTerminated(status);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      Status status = Utils.statusFromThrowable(cause);
      failWrites(status.asRuntimeException());
      if (ctx.channel().isActive()) {
        lifecycleManager.notifyShutdown(status);
        ctx.close();
      } else {
        lifecycleManager.notifyTerminated(status);
      }
    }

    /**
     * Buffers the write until either {@link #writeBufferedAndRemove(ChannelHandlerContext)} is
     * called, or we have somehow failed. If we have already failed in the past, then the write
     * will fail immediately.
     */
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
      /**
       * This check handles a race condition between Channel.write (in the calling thread) and the
       * removal of this handler (in the event loop thread).
       * The problem occurs in e.g. this sequence:
       * 1) [caller thread] The write method identifies the context for this handler
       * 2) [event loop] This handler removes itself from the pipeline
       * 3) [caller thread] The write method delegates to the invoker to call the write method in
       *    the event loop thread. When this happens, we identify that this handler has been
       *    removed with "bufferedWrites == null".
       */
      if (failCause != null) {
        promise.setFailure(failCause);
        ReferenceCountUtil.release(msg);
      } else {
        bufferedWrites.add(new ChannelWrite(msg, promise));
      }
    }

    /**
     * Calls to this method will not trigger an immediate flush. The flush will be deferred until
     * {@link #writeBufferedAndRemove(ChannelHandlerContext)}.
     */
    @Override
    public void flush(ChannelHandlerContext ctx) {
      /**
       * Swallowing any flushes is not only an optimization but also required
       * for the SslHandler to work correctly. If the SslHandler receives multiple
       * flushes while the handshake is still ongoing, then the handshake "randomly"
       * times out. Not sure at this point why this is happening. Doing a single flush
       * seems to work but multiple flushes don't ...
       */
      flushRequested = true;
    }

    /**
     * If we are still performing protocol negotiation, then this will propagate failures to all
     * buffered writes.
     */
    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
      Status status = Status.UNAVAILABLE.withDescription(
          "Connection closed while performing protocol negotiation (" + ctx.pipeline().first()
              + ')');
      failWrites(status.asRuntimeException());
      if (ctx.channel().isActive()) {
        lifecycleManager.notifyShutdown(status);
      } else {
        lifecycleManager.notifyTerminated(status);
      }
      super.close(ctx, future);
    }

    /**
     * Propagate failures to all buffered writes.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void failWrites(Throwable cause) {
      if (failCause == null) {
        failCause = cause;
      }
      while (!bufferedWrites.isEmpty()) {
        ChannelWrite write = bufferedWrites.poll();
        write.promise.setFailure(cause);
        ReferenceCountUtil.release(write.msg);
      }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      if (evt instanceof ProtocolNegotiationEvent) {
        writeBuffered(ctx);
        // Removal has to happen last as the above writes will likely trigger
        // new writes that have to be added to the end of queue in order to not
        // mess up the ordering.
        ctx.pipeline().remove(this);
      }
      super.userEventTriggered(ctx, evt);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void writeBuffered(ChannelHandlerContext ctx) {
      if (!ctx.channel().isActive() || writing) {
        return;
      }
      // Make sure that method can't be reentered, so that the ordering
      // in the queue can't be messed up.
      writing = true;
      while (!bufferedWrites.isEmpty()) {
        ChannelWrite write = bufferedWrites.poll();
        ctx.write(write.msg, write.promise);
      }
      if (flushRequested) {
        ctx.flush();
      }
    }

    private static final class ChannelWrite {
      final Object msg;
      final ChannelPromise promise;

      ChannelWrite(Object msg, ChannelPromise promise) {
        this.msg = msg;
        this.promise = promise;
      }
    }
  }
}
