package io.grpc.stub;

public interface GrpcSubscriber<T> {

  void onComplete();

  void onError(Throwable throwable);

  void onNext(T item);

  void onSubscribe(GrpcSubscription subscription);
}
