package io.grpc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.internal.ManagedChannelImpl.LbHelperImpl;
import io.grpc.internal.ManagedChannelImpl.NameResolverListenerImpl;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@VisibleForTesting
final class ChannelNameResolverListener implements NameResolver.Listener {
  private static final Logger logger =
      Logger.getLogger(ChannelNameResolverListener.class.getName());

  private final LoadBalancer balancer;
  private final LoadBalancer.Helper helper;

  ChannelNameResolverListener(LoadBalancer balancer, LoadBalancer.Helper helper) {
    this.balancer = checkNotNull(balancer, "balancer");
    this.helper = checkNotNull(helper, "helper");
  }

  @Override
  public void onAddresses(final List<EquivalentAddressGroup> servers, final Attributes config) {
    if (servers.isEmpty()) {
      onError(Status.UNAVAILABLE.withDescription("NameResolver returned an empty list"));
      return;
    }
    logger.log(Level.FINE, "[{0}] resolved address: {1}, config={2}",
        new Object[] {getLogId(), servers, config});
    helper.runSerialized(new Runnable() {
      @Override
      public void run() {
        // Call LB only if it's not shutdown.  If LB is shutdown, lbHelper won't match.
        if (NameResolverListenerImpl.this.helper != ManagedChannelImpl.this.lbHelper) {
          return;
        }
        try {
          balancer.handleResolvedAddressGroups(servers, config);
        } catch (Throwable e) {
          logger.log(
              Level.WARNING, "[" + getLogId() + "] Unexpected exception from LoadBalancer", e);
          // It must be a bug! Push the exception back to LoadBalancer in the hope that it may
          // be propagated to the application.
          balancer.handleNameResolutionError(Status.INTERNAL.withCause(e)
              .withDescription("Thrown from handleResolvedAddresses(): " + e));
        }
      }
    });
  }

  @Override
  public void onError(final Status error) {
    checkArgument(!error.isOk(), "the error status must not be OK");
    logger.log(Level.WARNING, "[{0}] Failed to resolve name. status={1}",
        new Object[] {getLogId(), error});
    channelExecutor.executeLater(new Runnable() {
      @Override
      public void run() {
        // Call LB only if it's not shutdown.  If LB is shutdown, lbHelper won't match.
        if (NameResolverListenerImpl.this.helper != ManagedChannelImpl.this.lbHelper) {
          return;
        }
        balancer.handleNameResolutionError(error);
      }
    }).drain();
  }
}
