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

package io.grpc.netty;

import com.google.common.base.Preconditions;
import io.grpc.internal.AbstractReadableBuffer;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A {@link java.nio.Buffer} implementation that is backed by a Netty {@link ByteBuf}. This class
 * does not call {@link ByteBuf#retain}, so if that is needed it should be called prior to creating
 * this buffer.
 */
class NettyReadableBuffer extends AbstractReadableBuffer {
  private final ByteBuf buffer;
  private boolean closed;

  NettyReadableBuffer(ByteBuf buffer) {
    this.buffer = Preconditions.checkNotNull(buffer, "buffer");
  }

  ByteBuf buffer() {
    return buffer;
  }

  @Override
  public int readableBytes() {
    return buffer.readableBytes();
  }

  @Override
  public void skipBytes(int length) {
    buffer.skipBytes(length);
  }

  @Override
  public Iterable<ByteBuffer> readonlyBuffers() {
    return Arrays.asList(buffer.nioBuffers());
  }

  @Override
  public int readUnsignedByte() {
    return buffer.readUnsignedByte();
  }

  @Override
  public int readInt() {
    return buffer.readInt();
  }

  /**
   * If the first call to close, calls {@link ByteBuf#release} to release the internal Netty buffer.
   */
  @Override
  public void close() {
    // Don't allow slices to close. Also, only allow close to be called once.
    if (!closed) {
      closed = true;
      buffer.release();
    }
  }
}
