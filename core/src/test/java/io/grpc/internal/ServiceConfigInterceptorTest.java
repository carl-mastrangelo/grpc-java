/*
 * Copyright 2018, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ServiceConfigInterceptor}.
 */
@RunWith(JUnit4.class)
public class ServiceConfigInterceptorTest {

  private final ServiceConfigInterceptor interceptor = new ServiceConfigInterceptor();

  private final String fullMethodName =
      MethodDescriptor.generateFullMethodName("service", "method");
  private final MethodDescriptor<Void, Void> methodDescriptor =
      MethodDescriptor.newBuilder(new NoopMarshaller(), new NoopMarshaller())
          .setType(MethodType.UNARY)
          .setFullMethodName(fullMethodName)
          .build();

  @Mock private Channel channel;
  @Captor private ArgumentCaptor<CallOptions> callOptionsCap;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private static final class JsonObj extends HashMap<String, Object> {
    private JsonObj(Object ... kv) {
      for (int i = 0; i < kv.length; i += 2) {
        put((String) kv[i], kv[i + 1]);
      }
    }
  }

  private static final class JsonList extends ArrayList<Object> {
    private JsonList(Object ... values) {
      addAll(Arrays.asList(values));
    }
  }

  @Test
  public void withWaitForReady() {
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "waitForReady", true);
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT.withoutWaitForReady(), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().isWaitForReady()).isTrue();
  }

  @Test
  public void withMaxRequestSize() {
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "maxRequestMessageBytes", 1d);
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT.withoutWaitForReady(), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().getMaxOutboundMessageSize()).isEqualTo(1);
  }

  @Test
  public void withMaxResponseSize() {
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "maxResponseMessageBytes", 1d);
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT.withoutWaitForReady(), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().getMaxInboundMessageSize()).isEqualTo(1);
  }

  @Test
  public void withoutWaitForReady() {
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "waitForReady", false);
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT.withWaitForReady(), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().isWaitForReady()).isFalse();
  }

  @Test
  public void fullMethodMatched() {
    // Put in service that matches, but has no deadline.  It should be lower priority
    JsonObj name1 = new JsonObj("service", "service");
    JsonObj methodConfig1 = new JsonObj("name", new JsonList(name1));

    JsonObj name2 = new JsonObj("service", "service", "method", "method");
    JsonObj methodConfig2 = new JsonObj("name", new JsonList(name2), "timeout", "1s");

    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig1, methodConfig2));

    interceptor.handleUpdate(serviceConfig);

    interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().getDeadline()).isNotNull();
  }

  @Test
  public void nearerDeadlineKept_existing() {
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "timeout", "100000s");
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    Deadline existingDeadline = Deadline.after(1000, TimeUnit.NANOSECONDS);
    interceptor.interceptCall(
        methodDescriptor, CallOptions.DEFAULT.withDeadline(existingDeadline), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().getDeadline()).isEqualTo(existingDeadline);
  }

  @Test
  public void nearerDeadlineKept_new() {
    // TODO(carl-mastrangelo): the deadlines are very large because they change over time.
    // This should be fixed, and is tracked in https://github.com/grpc/grpc-java/issues/2531
    JsonObj name = new JsonObj("service", "service");
    JsonObj methodConfig = new JsonObj("name", new JsonList(name), "timeout", "1s");
    JsonObj serviceConfig = new JsonObj("methodConfig", new JsonList(methodConfig));

    interceptor.handleUpdate(serviceConfig);

    Deadline existingDeadline = Deadline.after(1234567890, TimeUnit.NANOSECONDS);
    interceptor.interceptCall(
        methodDescriptor, CallOptions.DEFAULT.withDeadline(existingDeadline), channel);

    verify(channel).newCall(eq(methodDescriptor), callOptionsCap.capture());
    assertThat(callOptionsCap.getValue().getDeadline()).isNotEqualTo(existingDeadline);
  }

  private static final class NoopMarshaller implements MethodDescriptor.Marshaller<Void> {

    @Override
    public InputStream stream(Void value) {
      return null;
    }

    @Override
    public Void parse(InputStream stream) {
      return null;
    }
  }
}
