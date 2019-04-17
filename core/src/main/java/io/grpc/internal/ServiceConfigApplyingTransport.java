package io.grpc.internal;

import static io.opencensus.internal.Utils.checkNotNull;

import io.grpc.CallOptions;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.internal.ManagedChannelServiceConfig.MethodInfo;
import java.util.concurrent.TimeUnit;

final class ServiceConfigApplyingTransport extends ForwardingConnectionClientTransport {

  private final ConnectionClientTransport delegate;
  private volatile ManagedChannelServiceConfig config;

  ServiceConfigApplyingTransport(ConnectionClientTransport delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
  }

  @Override
  protected ConnectionClientTransport delegate() {
    return delegate;
  }

  @Override
  public ClientStream newStream(
      MethodDescriptor<?, ?> method, Metadata headers, CallOptions callOptions) {
    ManagedChannelServiceConfig localConfig = config;
    if (localConfig != null) {
      MethodInfo info = localConfig.getServiceMethodMap().get(method.getFullMethodName());
      if (info != null) {
        if (info.timeoutNanos != null) {
          Deadline newDeadline = Deadline.after(info.timeoutNanos, TimeUnit.NANOSECONDS);
          Deadline existingDeadline = callOptions.getDeadline();
          // If the new deadline is sooner than the existing deadline, swap them.
          if (existingDeadline == null || newDeadline.compareTo(existingDeadline) < 0) {
            callOptions = callOptions.withDeadline(newDeadline);
          }
        }
        if (info.waitForReady != null) {
          callOptions =
              info.waitForReady ? callOptions.withWaitForReady() : callOptions.withoutWaitForReady();
        }
        if (info.maxInboundMessageSize != null) {
          Integer existingLimit = callOptions.getMaxInboundMessageSize();
          if (existingLimit != null) {
            callOptions = callOptions.withMaxInboundMessageSize(
                Math.min(existingLimit, info.maxInboundMessageSize));
          } else {
            callOptions = callOptions.withMaxInboundMessageSize(info.maxInboundMessageSize);
          }
        }
        if (info.maxOutboundMessageSize != null) {
          Integer existingLimit = callOptions.getMaxOutboundMessageSize();
          if (existingLimit != null) {
            callOptions = callOptions.withMaxOutboundMessageSize(
                Math.min(existingLimit, info.maxOutboundMessageSize));
          } else {
            callOptions = callOptions.withMaxOutboundMessageSize(info.maxOutboundMessageSize);
          }
        }
      }
    }
    return super.newStream(method, headers, callOptions);
  }
}
