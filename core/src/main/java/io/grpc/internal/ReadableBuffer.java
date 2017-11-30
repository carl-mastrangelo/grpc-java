/*
 * Copyright 2014, gRPC Authors All rights reserved.
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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Interface for an abstract byte buffer. Buffers are intended to be a read-only, except for the
 * read position which is incremented after each read call.
 *
 * <p>Buffers may optionally expose a backing array for optimization purposes, similar to what is
 * done in {@link ByteBuffer}. It is not expected that callers will attempt to modify the backing
 * array.
 */
public interface ReadableBuffer extends Closeable {

  /**
   * Gets the current number of readable bytes remaining in this buffer.
   */
  int readableBytes();

  /**
   * Reads the next unsigned byte from this buffer and increments the read position by 1.
   *
   * @throws IndexOutOfBoundsException if required bytes are not readable
   */
  int readUnsignedByte();

  /**
   * Reads a 4-byte signed integer from this buffer using big-endian byte ordering. Increments the
   * read position by 4.
   *
   * @throws IndexOutOfBoundsException if required bytes are not readable
   */
  int readInt();

  /**
   * Increments the read position by the given length.
   *
   * @throws IndexOutOfBoundsException if required bytes are not readable
   */
  void skipBytes(int length);

  /**
   * Returns a readonly snapshot of the internal ByteBuffers that back this readable Buffer.
   */
  Iterable<ByteBuffer> readonlyBuffers();

  /**
   * Closes this buffer and releases any resources.
   */
  @Override
  void close();
}
