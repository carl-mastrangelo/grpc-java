package io.grpc;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.List;

@ExperimentalApi
public final class ServiceConfiguration {

  private ServiceConfiguration() {}

  public static final class ServiceConfigFile {
    private final List<ServiceConfigChoice> choice;

    private ServiceConfigFile(List<ServiceConfigChoice> choice) {
      this.choice = choice;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ServiceConfigFile)) {
        return false;
      }
      ServiceConfigFile that = (ServiceConfigFile) obj;

      return equal(this.choice, that.choice);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("choice", choice).toString();
    }

    @Override
    public int hashCode() {
      return hash(choice);
    }
  }

  private static final class ServiceConfigChoice {

    enum Language {
      UNSPECIFIED,
      CPP,
      JAVA,
      GO,
      PYTHON,
    }

    private final List<String> clientCluster;
    private final List<String> clientUser;
    private final List<Language> clientLanguage;
    private final ServiceConfig config;
    private final Integer percentage;

    private ServiceConfigChoice(
        List<String> clientCluster,
        List<String> clientUser,
        List<Language> clientLanguage,
        ServiceConfig config,
        Integer percentage) {
      this.clientCluster = clientCluster;
      this.clientUser = clientUser;
      this.clientLanguage = clientLanguage;
      this.config = config;
      this.percentage = percentage;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("clientCluster", clientCluster)
          .add("clientUser", clientUser)
          .add("clientLanguage", clientLanguage)
          .add("config", config)
          .add("percentage", percentage)
          .toString();
    }

    @Override
    public int hashCode() {
      return hash(clientCluster, clientUser, clientLanguage, config, percentage);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ServiceConfigChoice)) {
        return false;
      }
      ServiceConfigChoice that = (ServiceConfigChoice) obj;
      return equal(this.clientCluster, that.clientCluster)
          && equal(this.clientUser, that.clientUser)
          && equal(this.clientLanguage, that.clientLanguage)
          && equal(this.config, that.config)
          && equal(this.percentage, that.percentage);
    }
  }

  public static final class ServiceConfig {

    enum LoadBalancingPolicy {
      UNSPECIFIED,
      ROUND_ROBIN;
    }

    public static final class RetryThrottlingPolicy {

      private final int maxTokens;
      private final float tokenRatio;

      private RetryThrottlingPolicy(int maxTokens, float tokenRatio) {
        this.maxTokens = maxTokens;
        this.tokenRatio = tokenRatio;
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("maxTokens", maxTokens)
            .add("tokenRatio", tokenRatio)
            .toString();
      }

      @Override
      public int hashCode() {
        return hash(maxTokens, tokenRatio);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof RetryThrottlingPolicy)) {
          return false;
        }
        RetryThrottlingPolicy that = (RetryThrottlingPolicy) obj;
        return this.maxTokens == that.maxTokens && this.tokenRatio == that.tokenRatio;
      }
    }

    private final LoadBalancingPolicy loadBalancingPolicy;
    private final List<MethodConfig> methodConfig;
    private final RetryThrottlingPolicy retryThrottling;

    private ServiceConfig(
        LoadBalancingPolicy loadBalancingPolicy,
        List<MethodConfig> methodConfig,
        RetryThrottlingPolicy retryThrottling) {
      this.loadBalancingPolicy = loadBalancingPolicy;
      this.methodConfig = methodConfig;
      this.retryThrottling = retryThrottling;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("loadBalancingPolicy", loadBalancingPolicy)
          .add("methodConfig", methodConfig)
          .add("retryThrottling", retryThrottling)
          .toString();
    }

    @Override
    public int hashCode() {
      return hash(loadBalancingPolicy, methodConfig, retryThrottling);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ServiceConfig)) {
        return false;
      }
      ServiceConfig that = (ServiceConfig) obj;
      return equal(this.loadBalancingPolicy, that.loadBalancingPolicy)
          && equal(this.methodConfig, that.methodConfig)
          && equal(this.retryThrottling, that.retryThrottling);
    }
  }

  public static final class MethodConfig {

    public static final class Name {
      private final String service;
      private final String method;

      private Name(String service, String method) {
        this.service = service;
        this.method = method;
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("service", service)
            .add("method", method)
            .toString();
      }

      @Override
      public int hashCode() {
        return hash(service, method);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof Name)) {
          return false;
        }
        Name that = (Name) obj;
        return equal(this.service, that.service) && equal(this.method, that.method);
      }
    }

    public static final class RetryPolicy {
      private final int maxRetryAttempts;
      private final int initialBackoffMs;
      private final int maxBackoffMs;
      private final int backoffMultiplier;
      private final List<Status.Code> retryableStatusCodes;

      private RetryPolicy(
          int maxRetryAttempts,
          int initialBackoffMs,
          int maxBackoffMs,
          int backoffMultiplier,
          List<Status.Code> retryableStatusCodes) {
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.retryableStatusCodes = retryableStatusCodes;
      }
      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("maxRetryAttempts", maxRetryAttempts)
            .add("initialBackoffMs", initialBackoffMs)
            .add("maxBackoffMs", maxBackoffMs)
            .add("backoffMultiplier", backoffMultiplier)
            .add("retryableStatusCodes", retryableStatusCodes)
            .toString();
      }

      @Override
      public int hashCode() {
        return hash(
            maxRetryAttempts,
            initialBackoffMs,
            maxBackoffMs,
            backoffMultiplier,
            retryableStatusCodes);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof RetryPolicy)) {
          return false;
        }
        RetryPolicy that = (RetryPolicy) obj;
        return this.maxRetryAttempts == that.maxRetryAttempts
            && this.initialBackoffMs == initialBackoffMs
            && this.maxBackoffMs == maxBackoffMs
            && this.backoffMultiplier == backoffMultiplier
            equal(this.retryableStatusCodes, that.retryableStatusCodes);
      }

    }

    private final List<Name> name;
    private final Boolean waitForReady;
    /** Timeout in nanos */
    private final Long timeout;
    private final Integer maxRequestMessageBytes;
    private final Integer maxResponseMessageBytes;

    private MethodConfig(
        List<Name> name,
        Boolean waitForReady,
        Long timeout,
        Integer maxRequestMessageBytes,
        Integer maxResponseMessageBytes) {
        this.name = name;
        this.waitForReady = waitForReady;
        this.timeout = timeout;
        this.maxRequestMessageBytes = maxRequestMessageBytes;
        this.maxResponseMessageBytes = maxResponseMessageBytes;
    }
  }


  private static int hash(Object ... args) {
    return Arrays.hashCode(args);
  }

  private static boolean equal(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }
}
