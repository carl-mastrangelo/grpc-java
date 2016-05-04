package io.grpc;

import static io.grpc.internal.GrpcUtil.TIMER_SERVICE;

import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.internal.SharedResourceHolder;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ConnectionTimeoutInterceptor implements ClientInterceptor {

  private static final ScheduledExecutorService TIMER = SharedResourceHolder.get(TIMER_SERVICE);

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new CallMe<ReqT, RespT>(next.newCall(method, callOptions), 0, null);
  }

  private static final class CallMe<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>{

    private final ScheduledFuture<?> timeoutFuture;

    public CallMe(ClientCall<ReqT, RespT> delegate, long deadline, TimeUnit time) {
      super(delegate);
      timeoutFuture = TIMER.schedule(new Runnable() {
        @Override
        public void run() {
          cancel("Timed out", null);
        }
      }, deadline - System.nanoTime(), time);
    }

    @Override
    public void start(ClientCall.Listener<RespT> listener, Metadata headers) {
      super.start(new SimpleForwardingClientCallListener<RespT>(listener) {
        @Override
        public void onHeaders(Metadata headers) {
          timeoutFuture.cancel(false);
          super.onHeaders(headers);
        }
      }, headers);
    }
  }
}

