/*
 * Copyright 2019 The gRPC Authors
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
package io.grpc.netty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.grpc.Attributes;
import io.grpc.InternalChannelz;
import io.grpc.InternalChannelz.Security;
import javax.annotation.Nullable;

/**
 * Represents the completion of protocol negotiation.  {@link ProtocolNegotiator}'s are expected to
 * fire this event on the pipeline.
 */
final class ProtocolNegotiationEvent {

  static final ProtocolNegotiationEvent DEFAULT =
      new ProtocolNegotiationEvent(Attributes.EMPTY, /*security=*/ null);

  private final Attributes attributes;
  @Nullable private final Security security;

  private ProtocolNegotiationEvent(
      Attributes attributes, @Nullable InternalChannelz.Security security) {
    this.attributes = checkNotNull(attributes, "attributes");
    this.security = security;
  }

  public ProtocolNegotiationEvent withAttributes(Attributes attributes) {
    return new ProtocolNegotiationEvent(attributes, getSecurity());
  }

  public ProtocolNegotiationEvent withSecurity(@Nullable InternalChannelz.Security security) {
    return new ProtocolNegotiationEvent(getAttributes(), security);
  }

  public Attributes getAttributes() {
    return attributes;
  }

  @Nullable
  public Security getSecurity() {
    return security;
  }
}
