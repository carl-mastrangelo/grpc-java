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

package io.grpc;

/**
 * A linear map of keys and values.   Optimized for very small sizes.
 */
final class KeyValues {

  private static final Object[] EMTPY = new Object[0];

  private final Object[] kvs;

  KeyValues(Object[] kvs) {
    this.kvs = kvs;
  }

  KeyValues() {
    this(EMTPY);
  }

  int size() {
    return kvs.length / 2;
  }

  KeyValues put(Object k, Object v) {
    Object[] newkvs = new Object[kvs.length + 2];
    newkvs[0] = k;
    newkvs[1] = v;
    System.arraycopy(kvs, 0, newkvs, 2, kvs.length);
    return new KeyValues(newkvs);
  }

  Object get(Object k) {
    for (int i = 0; i < kvs.length; i += 2) {
      if (kvs[i] == k) {
        return kvs[i + 1];
      }
    }
    return null;
  }
}
