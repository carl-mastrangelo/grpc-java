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
  private final static int COUNT = 1000000000;

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
    final Semaphore sem = new Semaphore(10);
    final CountDownLatch latch = new CountDownLatch(COUNT);


    for (int i = 0; i < COUNT; i++) {
      sem.acquire();

      final ClientCall<Void, Void> call = channel.newCall(METHOD, CallOptions.DEFAULT);
      ClientCall.Listener<Void> listen =
          new ClientCall.Listener<Void>() {
            @Override
            public void onMessage(Void message) {
              call.cancel();

            }
            @Override
            public void onClose(Status status, Metadata trailers) {
              System.out.println(trailers);
              latch.countDown();
              sem.release();
            }
          };
      call.start(listen, new Metadata());
      call.sendMessage(null);
      call.halfClose();
      call.request(1);
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

