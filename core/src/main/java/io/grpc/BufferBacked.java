/*
 * Copyright 2017, gRPC Authors All rights reserved.
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

import java.nio.ByteBuffer;

/**
 * Tagging interface for KnownLength input streams that are back by NIO buffers.
 */
public interface BufferBacked extends KnownLength {

  /**
   * Returns an iterable of ByteBuffers.  The returned Iterable is only valid until the next call
   * of {@link #readonlyBuffers}.
   */
  Iterable<ByteBuffer> readonlyBuffers();
}
