package io.grpc.stub;

public interface GrpcPublisher<T> {
  void subscribe(GrpcSubscriber<T> subscriber);
}
