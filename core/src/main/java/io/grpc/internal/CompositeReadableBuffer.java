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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * A {@link ReadableBuffer} that is composed of 0 or more {@link ReadableBuffer}s. This provides a
 * facade that allows multiple buffers to be treated as one.
 *
 * <p>When a buffer is added to a composite, its life cycle is controlled by the composite. Once
 * the composite has read past the end of a given buffer, that buffer is automatically closed and
 * removed from the composite.
 */
public class CompositeReadableBuffer extends AbstractReadableBuffer {

  private int readableBytes;
  private final Queue<ReadableBuffer> buffers = new ArrayDeque<ReadableBuffer>();

  /**
   * Adds a new {@link ReadableBuffer} at the end of the buffer list. After a buffer is added, it is
   * expected that this {@code CompositeBuffer} has complete ownership. Any attempt to modify the
   * buffer (i.e. modifying the readable bytes) may result in corruption of the internal state of
   * this {@code CompositeBuffer}.
   */
  public void addBuffer(ReadableBuffer buffer) {
    if (!(buffer instanceof CompositeReadableBuffer)) {
      buffers.add(buffer);
      readableBytes += buffer.readableBytes();
      return;
    }

    CompositeReadableBuffer compositeBuffer = (CompositeReadableBuffer) buffer;
    while (!compositeBuffer.buffers.isEmpty()) {
      ReadableBuffer subBuffer = compositeBuffer.buffers.remove();
      buffers.add(subBuffer);
    }
    readableBytes += compositeBuffer.readableBytes;
    compositeBuffer.readableBytes = 0;
    compositeBuffer.close();
  }

  @Override
  public int readableBytes() {
    return readableBytes;
  }

  @Override
  public int readUnsignedByte() {
    while (!buffers.isEmpty()) {
      ReadableBuffer buf = buffers.peek();
      if (buf.readableBytes() != 0) {
        readableBytes--;
        return buf.readUnsignedByte();
      }
      buffers.poll().close();
    }
    throw new IndexOutOfBoundsException();
  }

  @Override
  public void skipBytes(int length) {
    int remaining = length;
    while (!buffers.isEmpty() && remaining != 0) {
      ReadableBuffer buf = buffers.peek();
      int toSkip = Math.min(buf.readableBytes(), remaining);
      if (toSkip != 0) {
        buf.skipBytes(toSkip);
        readableBytes -= toSkip;
      } else {
        buffers.poll().close();
      }
    }
    if (remaining != 0) {
      throw new IndexOutOfBoundsException();
    }
  }

  @Override
  public Iterable<ByteBuffer> readonlyBuffers() {
    return new Iterable<ByteBuffer>() {

      @Override
      public Iterator<ByteBuffer> iterator() {
        return new Iterator<ByteBuffer>() {

          @Override
          public boolean hasNext() {
            return false;
          }

          @Override
          public ByteBuffer next() {
            return null;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @Override
  public void close() {
    while (!buffers.isEmpty()) {
      buffers.remove().close();
    }
  }
}
