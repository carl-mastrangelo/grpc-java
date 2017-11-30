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

import static com.google.common.base.Charsets.UTF_8;

import com.google.common.base.Preconditions;
import io.grpc.BufferBacked;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;

/**
 * Utility methods for creating {@link ReadableBuffer} instances.
 */
public final class ReadableBuffers {
  private static final ReadableBuffer EMPTY_BUFFER = new ByteArrayWrapper(new byte[0]);

  /**
   * Returns an empty {@link ReadableBuffer} instance.
   */
  public static ReadableBuffer empty() {
    return EMPTY_BUFFER;
  }

  /**
   * Shortcut for {@code wrap(bytes, 0, bytes.length}.
   */
  public static ReadableBuffer wrap(byte[] bytes) {
    return new ByteArrayWrapper(bytes, 0, bytes.length);
  }

  /**
   * Creates a new {@link ReadableBuffer} that is backed by the given byte array.
   *
   * @param bytes the byte array being wrapped.
   * @param offset the starting offset for the buffer within the byte array.
   * @param length the length of the buffer from the {@code offset} index.
   */
  public static ReadableBuffer wrap(byte[] bytes, int offset, int length) {
    return new ByteArrayWrapper(bytes, offset, length);
  }

  /**
   * Creates a new {@link ReadableBuffer} that is backed by the given {@link ByteBuffer}. Calls to
   * read from the buffer will increment the position of the {@link ByteBuffer}.
   */
  public static ReadableBuffer wrap(ByteBuffer bytes) {
    return new ByteReadableBufferWrapper(bytes);
  }

  /**
   * Reads the entire {@link ReadableBuffer} to a new {@link String} with the given charset.
   */
  public static String readAsString(ReadableBuffer buffer, Charset charset) {
    Preconditions.checkNotNull(charset, "charset");
    int bytesToRead = buffer.readableBytes();

    ByteBuffer scratchBuffer = ByteBuffer.allocate(bytesToRead);
    for (ByteBuffer buf : buffer.readonlyBuffers()) {
      scratchBuffer.put(buf);
    }
    buffer.skipBytes(bytesToRead);
    return new String(scratchBuffer.array(), charset);
  }

  /**
   * Reads the entire {@link ReadableBuffer} to a new {@link String} using UTF-8 decoding.
   */
  public static String readAsStringUtf8(ReadableBuffer buffer) {
    return readAsString(buffer, UTF_8);
  }

  /**
   * Creates a new {@link InputStream} backed by the given buffer. Any read taken on the stream will
   * automatically increment the read position of this buffer. Closing the stream, however, does not
   * affect the original buffer.
   *
   * @param buffer the buffer backing the new {@link InputStream}.
   * @param owner if {@code true}, the returned stream will close the buffer when closed.
   */
  public static InputStream openStream(ReadableBuffer buffer, boolean owner) {
    return new BufferInputStream(owner ? buffer : ignoreClose(buffer));
  }

  /**
   * Decorates the given {@link ReadableBuffer} to ignore calls to {@link ReadableBuffer#close}.
   *
   * @param buffer the buffer to be decorated.
   * @return a wrapper around {@code buffer} that ignores calls to {@link ReadableBuffer#close}.
   */
  public static ReadableBuffer ignoreClose(ReadableBuffer buffer) {
    return new ForwardingReadableBuffer(buffer) {
      @Override
      public void close() {
        // Ignore.
      }
    };
  }

  /**
   * A {@link ReadableBuffer} that is backed by a byte array.
   */
  private static class ByteArrayWrapper extends AbstractReadableBuffer {
    int offset;
    final int end;
    final byte[] bytes;

    ByteArrayWrapper(byte[] bytes) {
      this(bytes, 0, bytes.length);
    }

    ByteArrayWrapper(byte[] bytes, int offset, int length) {
      Preconditions.checkArgument(offset >= 0, "offset must be >= 0");
      Preconditions.checkArgument(length >= 0, "length must be >= 0");
      Preconditions.checkArgument(offset + length <= bytes.length,
          "offset + length exceeds array boundary");
      this.bytes = Preconditions.checkNotNull(bytes, "bytes");
      this.offset = offset;
      this.end = offset + length;
    }

    @Override
    public int readableBytes() {
      return end - offset;
    }

    @Override
    public void skipBytes(int length) {
      checkReadable(length);
      offset += length;
    }

    @Override
    public Iterable<ByteBuffer> readonlyBuffers() {
      return Collections.singletonList(
          ByteBuffer.wrap(bytes, offset, readableBytes()).asReadOnlyBuffer());
    }

    @Override
    public int readUnsignedByte() {
      checkReadable(1);
      return bytes[offset++] & 0xFF;
    }
  }

  /**
   * A {@link ReadableBuffer} that is backed by a {@link ByteBuffer}.
   */
  private static class ByteReadableBufferWrapper extends AbstractReadableBuffer {
    final ByteBuffer bytes;

    ByteReadableBufferWrapper(ByteBuffer bytes) {
      this.bytes = Preconditions.checkNotNull(bytes, "bytes");
    }

    @Override
    public int readableBytes() {
      return bytes.remaining();
    }

    @Override
    public int readUnsignedByte() {
      checkReadable(1);
      return bytes.get() & 0xFF;
    }

    @Override
    public void skipBytes(int length) {
      checkReadable(length);
      bytes.position(bytes.position() + length);
    }

    @Override
    public Iterable<ByteBuffer> readonlyBuffers() {
      return Collections.singletonList(bytes.asReadOnlyBuffer());
    }
  }

  /**
   * An {@link InputStream} that is backed by a {@link ReadableBuffer}.
   */
  private static final class BufferInputStream extends InputStream implements BufferBacked {
    final ReadableBuffer buffer;

    public BufferInputStream(ReadableBuffer buffer) {
      this.buffer = Preconditions.checkNotNull(buffer, "buffer");
    }

    @Override
    public int available() throws IOException {
      return buffer.readableBytes();
    }

    @Override
    public int read() {
      if (buffer.readableBytes() == 0) {
        // EOF.
        return -1;
      }
      return buffer.readUnsignedByte();
    }

    @Override
    public int read(byte[] dest, int destOffset, final int length) throws IOException {
      if (buffer.readableBytes() == 0) {
        // EOF.
        return -1;
      }

      final int toRead = Math.min(buffer.readableBytes(), length);
      int remaining = toRead;
      for (ByteBuffer buf : readonlyBuffers()) {
        int rem = Math.min(buf.remaining(), remaining);
        buf.get(dest, destOffset, rem);
        if ((remaining -= rem) <= 0) {
          break;
        }
      }
      buffer.skipBytes(toRead);
      return toRead;
    }

    @Override
    public Iterable<ByteBuffer> readonlyBuffers() {
      return buffer.readonlyBuffers();
    }
  }

  private ReadableBuffers() {}
}
