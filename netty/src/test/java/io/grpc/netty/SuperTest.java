/*
 * Copyright 2015, gRPC Authors All rights reserved.
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Created by notcarl on 8/15/17.
 */
@RunWith(JUnit4.class)
public class SuperTest {

  private static final Logger logger = Logger.getLogger(SuperTest.class.getName());

  private byte[][] data;

  private byte[][] initGarbage() {

    int subarrays = 1000;
    byte[][] arr = new byte[subarrays][];
    for (int i = 0; i < subarrays; i++) {
      arr[i] = new byte[1 << 20];
    }
    return arr;
  }


  private long gc() {
    long start = System.nanoTime();
    System.gc();
    return System.nanoTime() - start;
  }

  @Test
  public void sleeper() throws Exception {
    for (int i = 0; i < 60; i++) {
      runsleep();
    }
  }

  private void runsleep() throws Exception {
    NioEventLoopGroup elg = new NioEventLoopGroup(1);

    final AtomicLong requests = new AtomicLong();
    final AtomicLong responses = new AtomicLong();

    Bootstrap b = new Bootstrap()
        .group(elg)
        .channel(NioSocketChannel.class)
        .handler(new SimpleChannelInboundHandler<ByteBuf>() {

          @Override
          public void channelActive(ChannelHandlerContext ctx) throws Exception {
            logger.info("connected");
            super.channelActive(ctx);
          }

          @Override
          protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            responses.addAndGet(msg.readableBytes());
          }
        });

    ServerBootstrap sb = new ServerBootstrap()
        .group(elg)
        .channel(NioServerSocketChannel.class)
        .localAddress(0)
        .childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
          @Override
          public void channelActive(ChannelHandlerContext ctx) throws Exception {
            logger.info("got connection");
            super.channelActive(ctx);
          }

          @Override
          protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            ctx.writeAndFlush(msg.copy());
          }
        });

    SocketAddress addr = sb.bind().sync().channel().localAddress();

    final Channel c = b.connect(addr).sync().channel();

    ScheduledFuture<?> writer = c.eventLoop()
        .scheduleAtFixedRate(new Runnable() {
          @Override
          public void run() {
            c.writeAndFlush(c.alloc().buffer(1).writeByte('a'));
          }
        }, 0, 25, TimeUnit.MILLISECONDS);
    final AtomicLong lastResponses = new AtomicLong();

    ScheduledFuture<?> checker = c.eventLoop().scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        long old = lastResponses.get();
        if (lastResponses.get() == responses.get()) {
          c.close();
        }
        lastResponses.set(responses.get());
      }
    }, 100, 100, TimeUnit.MILLISECONDS);

    long start = System.nanoTime();
    while (!c.closeFuture().isDone() && (System.nanoTime() - start) < TimeUnit.SECONDS.toNanos(1)) {

      data = initGarbage();

      // attempt to force tenure;
      for (int i = 0; i < 20; i++) {
        logger.fine("init gc took " + TimeUnit.NANOSECONDS.toMillis(gc()) + "ms");
      }
      data = null;

      logger.info("slow gc took " + TimeUnit.NANOSECONDS.toMillis(gc()) + "ms");

      //Thread.sleep(1000);
    }



    Error error = null;
    if (c.closeFuture().isDone()) {
      error = new AssertionError("Dropped keepalives");
    }

    checker.cancel(false);
    writer.cancel(false);

    elg.shutdownGracefully();
    if (error != null) {
      throw error;
    }
  }
}
