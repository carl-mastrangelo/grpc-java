package io.grpc.stub;

public interface GrpcSubscription {
  void cancel();

  void request(long n);
}
