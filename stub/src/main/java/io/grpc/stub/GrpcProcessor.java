package io.grpc.stub;

public interface GrpcProcessor<T, R> extends GrpcSubscriber<T>, GrpcPublisher<R> {
}
