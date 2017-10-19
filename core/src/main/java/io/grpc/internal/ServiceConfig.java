package io.grpc.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Arrays;

final class ServiceConfig {

  public static final class MethodInfo {
    final Long timeoutNanos;
    final Boolean waitForReady;
    final Integer maxInboundMessageSize;
    final Integer maxOutboundMessageSize;

    /**
     * All arguments are optional.
     */
    public MethodInfo(
        Long timeoutNanos,
        Boolean waitForReady,
        Integer maxInboundMessageSize,
        Integer maxOutboundMessageSize) {
      this.timeoutNanos = timeoutNanos;
      this.waitForReady = waitForReady;
      this.maxInboundMessageSize = maxInboundMessageSize;
      this.maxOutboundMessageSize = maxOutboundMessageSize;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(
          new Object[]{timeoutNanos, waitForReady, maxInboundMessageSize, maxOutboundMessageSize});
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof MethodInfo)) {
        return false;
      }
      MethodInfo that = (MethodInfo) other;
      return equal(this.timeoutNanos, that.timeoutNanos)
          && equal(this.waitForReady, that.waitForReady)
          && equal(this.maxInboundMessageSize, that.maxInboundMessageSize)
          && equal(this.maxOutboundMessageSize, that.maxOutboundMessageSize);
    }

    private boolean equal(Object left, Object right) {
      return left == null ? right == null : left.equals(right);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("timeoutNanos", timeoutNanos)
          .add("waitForReady", waitForReady)
          .add("maxInboundMessageSize", maxInboundMessageSize)
          .add("maxOutboundMessageSize", maxOutboundMessageSize)
          .toString();
    }
  }
}
