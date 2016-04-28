/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.examples.helloworld;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Drainable;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class NoiseClient {
  private  static final int COUNT = 1000000000;

  /** Checkstyle sucks. */
  public static void main(String []args) throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(2);

    Server server =
        ServerBuilder.forPort(9997)
            .executor(es)
            .addService(SERVER_DEF)
            .build()
            .start();

    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", 9997)
            .executor(es)
            .usePlaintext(true)
            .build();
    final Semaphore sem = new Semaphore(1);
    final CountDownLatch latch = new CountDownLatch(COUNT);


    for (int i = 0; i < COUNT; i++) {
      final ClientCall<Void, Void> call = channel.newCall(METHOD, CallOptions.DEFAULT);
      ClientCall.Listener<Void> listen =
          new ClientCall.Listener<Void>() {
          @Override
          public void onHeaders(Metadata headers) {
            System.err.println("headers " + call);

          }

            @Override
            public void onMessage(Void message) {
              System.err.println("cancelled " + call);
              call.cancel();
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
              System.err.println("closed " + call);
              latch.countDown();
              sem.release();
            }
          };
      sem.acquire();
      call.start(listen, new Metadata());
      System.err.println("started " + call);
      call.sendMessage(null);
      call.halfClose();
      call.request(1);
      System.err.println("sent " + call);
    }

    latch.await();
    server.shutdown();
  }

  private static final MethodDescriptor<Void, Void> METHOD =
      MethodDescriptor.create(
          MethodType.UNARY,
          "foo/bar",
          new Marshaller<Void>() {

            @Override
            public InputStream stream(Void value) {
              return VOID_STREAM;
            }

            @Override
            public Void parse(InputStream stream) {
              return null;
            }
          },
          new Marshaller<Void>() {

            @Override
            public InputStream stream(Void value) {
              return VOID_STREAM;
            }

            @Override
            public Void parse(InputStream stream) {
              return null;
            }
          });

  private static final ServerServiceDefinition SERVER_DEF =
      ServerServiceDefinition.builder("foo")
          .addMethod(
              METHOD,
              new ServerCallHandler<Void, Void>() {
                @Override
                public ServerCall.Listener<Void> startCall(
                    MethodDescriptor<Void, Void> method,
                    final ServerCall<Void> call,
                    Metadata headers) {
                  call.sendHeaders(new Metadata());
                  call.request(1);
                  call.sendMessage(null);

                  return new ServerCall.Listener<Void>() {


                    @Override
                    public void onCancel() {
                      // noop
                    }

                    @Override
                    public void onHalfClose() {
                      call.close(Status.OK, new Metadata());
                    }
                  };
                }
              })
          .build();

  private static final VoidInputStream VOID_STREAM = new VoidInputStream();

  private static final class VoidInputStream extends InputStream implements Drainable {
    @Override
    public int available() {
      return 0;
    }

    @Override
    public int read() {
      return -1;
    }

    @Override
    public int drainTo(OutputStream target) {
      return 0;
    }
  }
}

