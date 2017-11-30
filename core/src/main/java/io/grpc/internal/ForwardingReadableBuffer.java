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

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Base class for a wrapper around another {@link ReadableBuffer}.
 *
 * <p>This class just passes every operation through to the underlying buffer. Subclasses may
 * override methods to intercept certain operations.
 */
public abstract class ForwardingReadableBuffer implements ReadableBuffer {

  private final ReadableBuffer buf;

  /**
   * Constructor.
   *
   * @param buf the underlying buffer
   */
  public ForwardingReadableBuffer(ReadableBuffer buf) {
    this.buf = Preconditions.checkNotNull(buf, "buf");
  }

  @Override
  public int readableBytes() {
    return buf.readableBytes();
  }

  @Override
  public int readUnsignedByte() {
    return buf.readUnsignedByte();
  }

  @Override
  public int readInt() {
    return buf.readInt();
  }

  @Override
  public void skipBytes(int length) {
    buf.skipBytes(length);
  }

  @Override
  public Iterable<ByteBuffer> readonlyBuffers() {
    return buf.readonlyBuffers();
  }

  @Override
  public void close() {
    buf.close();
  }
}
