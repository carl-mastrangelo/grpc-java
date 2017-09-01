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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.grpc.Codec;
import io.grpc.Decompressor;
import io.grpc.Status;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Deframer for GRPC frames.
 *
 * <p>This class is not thread-safe. Unless otherwise stated, all calls to public methods should be
 * made in the deframing thread.
 */
@NotThreadSafe
public class MessageDeframer implements Closeable, Deframer {
  private static final int HEADER_LENGTH = 5;
  private static final int COMPRESSED_FLAG_MASK = 1;
  private static final int RESERVED_MASK = 0xFE;

  /**
   * A listener of deframing events. These methods will be invoked from the deframing thread.
   */
  public interface Listener {

    /**
     * Called when the given number of bytes has been read from the input source of the deframer.
     * This is typically used to indicate to the underlying transport that more data can be
     * accepted.
     *
     * @param numBytes the number of bytes read from the deframer's input source.
     */
    void bytesRead(int numBytes);

    /**
     * Called to deliver the next complete message.
     *
     * @param producer single message producer wrapping the message.
     */
    void messagesAvailable(StreamListener.MessageProducer producer);

    /**
     * Called when the deframer closes.
     *
     * @param hasPartialMessage whether the deframer contained an incomplete message at closing.
     */
    void deframerClosed(boolean hasPartialMessage);

    /**
     * Called when a {@link #deframe(ReadableBuffer)} operation failed.
     *
     * @param cause the actual failure
     */
    void deframeFailed(Throwable cause);
  }

  /**
   * Represents all state about messages currently being deframed.
   */
  private MessageState messageState = new MessageState();

  private static final class MessageState {
    // Are we currently delivering messages.
    boolean delivering;
    // what part of the message are we decoding
    boolean deframingBody;

    ByteBuffer headerBytes = ByteBuffer.allocate(HEADER_LENGTH);

    /** The size of the message body.  Only valid while {@link #deframingBody} is true. */
    int bodySizeBytes;
    /** True if the body is compressed.  Only valid while {@link #deframingBody} is true. */
    boolean isCompressed;

    /** Only valid while delivering.  Must be 0 after delivery.  */
    long numBytesRead;

    /** Data that is requested, but is not yet fully present.  */
    Deque<ReadableBuffer> requested;

    /** The number of bytes in {@link #requested} */
    int requestedDataBytes;

    /** Data that has yet to be processed.  Destination for buffers not yet requested by the app. */
    Deque<ReadableBuffer> unrequested;

    boolean hasUnrequestedData() {
      return !(unrequested == null || unrequested.isEmpty());
    }

    boolean hasRequestedData() {
      return !(requested == null || requested.isEmpty());
    }

    void addUnrequestedLast(ReadableBuffer b) {
      if (b.readableBytes() == 0) {
        throw new AssertionError(); // TODO(carl-mastrangelo): remove this
      }
      getUnrequested0().addLast(b);
    }

    void addUnrequestedFirst(ReadableBuffer b) {
      if (b.readableBytes() == 0) {
        throw new AssertionError(); // TODO(carl-mastrangelo): remove this
      }
      getUnrequested0().addFirst(b);
    }

    ReadableBuffer getUnrequestedFirst() {
      return unrequested.getFirst();
    }

    void addRequestedLast(ReadableBuffer b) {
      if (b.readableBytes() == 0) {
        throw new AssertionError(); // TODO(carl-mastrangelo): remove this
      }
      getRequested0().addLast(b);
      requestedDataBytes += b.readableBytes();
    }

    void addRequestedFirst(ReadableBuffer b) {
      if (b.readableBytes() == 0) {
        throw new AssertionError(); // TODO(carl-mastrangelo): remove this
      }
      getRequested0().addFirst(b);
      requestedDataBytes += b.readableBytes();
    }

    ReadableBuffer getRequestedFirst() {
      ReadableBuffer b = requested.getFirst();
      requestedDataBytes -= b.readableBytes();
      return b;
    }

    ReadableBuffer getRequestedLast() {
      ReadableBuffer b = requested.getLast();
      requestedDataBytes -= b.readableBytes();
      return b;
    }

    private Deque<ReadableBuffer> getUnrequested0() {
      if (unrequested == null) {
        unrequested = new ArrayDeque<ReadableBuffer>(4);
      }
      return unrequested;
    }

    private Deque<ReadableBuffer> getRequested0() {
      if (requested == null) {
        requested = new ArrayDeque<ReadableBuffer>(4);
      }
      return requested;
    }
  }

  private final Listener listener;
  private int maxInboundMessageSize;
  private final StatsTraceContext statsTraceCtx;
  private final String debugString;
  private Decompressor decompressor;
  private long pendingDeliveries;

  private boolean closeWhenComplete = false;
  private volatile boolean stopDelivery = false;

  /**
   * Create a deframer.
   *
   * @param listener listener for deframer events.
   * @param decompressor the compression used if a compressed frame is encountered, with
   *  {@code NONE} meaning unsupported
   * @param maxMessageSize the maximum allowed size for received messages.
   * @param debugString a string that will appear on errors statuses
   */
  public MessageDeframer(Listener listener, Decompressor decompressor, int maxMessageSize,
      StatsTraceContext statsTraceCtx, String debugString) {
    this.listener = Preconditions.checkNotNull(listener, "sink");
    this.decompressor = Preconditions.checkNotNull(decompressor, "decompressor");
    this.maxInboundMessageSize = maxMessageSize;
    this.statsTraceCtx = checkNotNull(statsTraceCtx, "statsTraceCtx");
    this.debugString = debugString;
  }

  @Override
  public void setMaxInboundMessageSize(int messageSize) {
    maxInboundMessageSize = messageSize;
  }

  @Override
  public void setDecompressor(Decompressor decompressor) {
    this.decompressor = checkNotNull(decompressor, "Can't pass an empty decompressor");
  }

  @Override
  public void request(int numMessages) {
    Preconditions.checkArgument(numMessages > 0, "numMessages must be > 0");
    if (isClosed()) {
      return;
    }
    pendingDeliveries += numMessages;
    deliver(messageState, null);
  }

  @Override
  public void deframe(ReadableBuffer data) {
    Preconditions.checkNotNull(data, "data");

    boolean needToCloseData = true;
    try {
      if (!isClosedOrScheduledToClose()) {
        if (data.readableBytes() != 0) {
          deliver(messageState, data);
          needToCloseData = false;
        }
      }
    } finally {
      if (needToCloseData) {
        data.close();
      }
    }
  }

  @Override
  public void closeWhenComplete() {
    if (isClosed()) {
      return;
    }
    closeWhenCompleteInternal(messageState);
  }

  private void closeWhenCompleteInternal(MessageState state) {
    boolean stalled = !state.hasUnrequestedData();
    if (stalled) {
      close();
    } else {
      closeWhenComplete = true;
    }
  }

  /**
   * Sets a flag to interrupt delivery of any currently queued messages. This may be invoked outside
   * of the deframing thread, and must be followed by a call to {@link #close()} in the deframing
   * thread. Without a subsequent call to {@link #close()}, the deframer may hang waiting for
   * additional messages before noticing that the {@code stopDelivery} flag has been set.
   */
  void stopDelivery() {
    stopDelivery = true;
  }

  @Override
  public void close() {
    if (isClosed()) {
      return;
    }
    try {
      closeInternal(messageState);
    } finally {
      messageState = null;
    }
  }

  private void closeInternal(MessageState state) {
    boolean hasPartialHeader =
        !state.deframingBody && state.headerBytes.remaining() != HEADER_LENGTH;
    boolean hasPartialBody = state.deframingBody && state.hasRequestedData();

    ReadableBuffer bufferToClose;
    if (state.hasUnrequestedData()) {
      while ((bufferToClose = state.unrequested.poll()) != null) {
        bufferToClose.close();
      }
      state.unrequested = null;
    }
    if (state.hasRequestedData()) {
      while ((bufferToClose = state.requested.poll()) != null) {
        bufferToClose.close();
      }
      state.requested = null;
    }

    listener.deframerClosed(hasPartialHeader || hasPartialBody);
  }

  /**
   * Indicates whether or not this deframer has been closed.
   */
  public boolean isClosed() {
    return messageState == null;
  }

  /** Returns true if this deframer has already been closed or scheduled to close. */
  private boolean isClosedOrScheduledToClose() {
    return isClosed() || closeWhenComplete;
  }

  private void deliver(MessageState state, @Nullable ReadableBuffer buf) {
    // We can have reentrancy here when using a direct executor, triggered by calls to
    // request more messages. This is safe as we simply loop until pendingDelivers = 0
    if (state.delivering) {
      checkArgument(buf == null, "buf should be null: %s", buf);
      return;
    }
    assert state.numBytesRead == 0;
    state.delivering = true;
    try {
      deliverNonReentrant(state, buf);
    } finally {
      while (state.numBytesRead > Integer.MAX_VALUE) {
        listener.bytesRead(Integer.MAX_VALUE);
        state.numBytesRead -= Integer.MAX_VALUE;
      }
      listener.bytesRead((int) state.numBytesRead);
      state.numBytesRead = 0;
      state.delivering = false;
    }
  }

  /**
   * Delivers messages to the application until there are no more requests, there is not enough
   * data, or the deframer is closing down.  It is not resilient to reentrancy.
   *
   * @param state the current deframing state.
   * @param inBuf the buffer to decode.  May be null if there is no incoming data.
   */
  private void deliverNonReentrant(MessageState state, @Nullable ReadableBuffer inBuf) {
    ReadableBuffer current = maybeEnqueueBuffer(state, inBuf);

    boolean currentEmpty = current == null || current.readableBytes() == 0;
    boolean madeDelivery;
    while (!stopDelivery && pendingDeliveries > 0) {
      if (currentEmpty) {
        if (state.hasRequestedData()) {
          current = state.getRequestedFirst();
        } else if (state.hasUnrequestedData()) {
          current = state.getUnrequestedFirst();
        } else {
          break;
        }
      }
      if ((madeDelivery = deliverSingleBuffer(state, current))) {
        pendingDeliveries--;
      }
      if ((currentEmpty = current.readableBytes() == 0)) {
        current.close();
        current = null;
      } else if (!madeDelivery) {
        break;
      }
    }

    // If there is left over data, put it on the front of the appropriate queue.
    if (!currentEmpty) {
      if (pendingDeliveries > 0) {
        state.addRequestedFirst(current);
      } else {
        state.addUnrequestedFirst(current);
      }
    } else if (current != null) {
      current.close();
    }

    if (stopDelivery) {
      close();
      return;
    }
    /*
     * We are stalled when there are no more bytes to process. This allows delivering errors as
     * soon as the buffered input has been consumed, independent of whether the application
     * has requested another message.  At this point in the function, either all frames have been
     * delivered, or unprocessed is empty.  If there is a partial message, it will be inside next
     * state.requested and not in state.unrequested.  If there is extra data but no pending
     * deliveries, it will be in state.unrequested.
     */
    boolean stalled = !state.hasUnrequestedData();
    if (closeWhenComplete && stalled) {
      close();
    }
  }

  /**
   * Either enqueues the buffer into the correct queue, or returns it for immediate use.
   * @return the inputted buffer if it was not enqueued, else null.
   */
  @Nullable
  private ReadableBuffer maybeEnqueueBuffer(MessageState state, @Nullable ReadableBuffer inBuf) {
    if (inBuf != null) {
      if (state.hasUnrequestedData() || pendingDeliveries == 0) {
        // If there is already unrequested data, just tack it on.
        state.addUnrequestedLast(inBuf);
      } else if (state.hasRequestedData()) {
        // If there is already requested data, we know there was not enough last time, and that
        // there is at least one outstanding request.
        assert pendingDeliveries > 0;
        state.addRequestedLast(inBuf);
      } else {
        // There is at least one pending delivery and there is neither requested or unrequested
        // data.  We should process the data inline.
        return inBuf;
      }
    }
    return null;
  }

  /**
   * Delivers up to one message given the inputted buffer.
   *
   * @return true if a message was delivered.
   */
  private boolean deliverSingleBuffer(MessageState state, ReadableBuffer buf) {
    assert buf != null;
    assert pendingDeliveries > 0;
    assert !stopDelivery;
    // TODO(carl-mastrangelo): report overflow

    if (!state.deframingBody) {
      if (state.headerBytes.remaining() == HEADER_LENGTH
          && buf.readableBytes() >= HEADER_LENGTH) {
        int flags = buf.readUnsignedByte();
        int size = buf.readInt();
        handleHeader(state, flags, size);
        state.numBytesRead += HEADER_LENGTH;
      } else {
        slowReadHeader(state, buf);
      }
    }
    if (state.deframingBody) {
      if (state.bodySizeBytes <= buf.readableBytes() + state.requestedDataBytes) {
        // TODO(carl-mastrangelo): Use proper slicing and avoid this needless copy.
        ReadableBuffer dst;
        if (buf.readableBytes() > state.bodySizeBytes) {
          dst = buf.readBytes(state.bodySizeBytes);
          // push the remaining back into requested.  The outer code will push it to the correct
          // place.
          state.addRequestedFirst(buf);
        } else if (buf.readableBytes() == state.bodySizeBytes) {
          dst = buf.readBytes(state.bodySizeBytes); // Slice to "consume" the data
        } else {
          CompositeReadableBuffer cdst = new CompositeReadableBuffer();
          dst = cdst;
          int bytesRemaining = state.bodySizeBytes;
          do {
            if (buf.readableBytes() < bytesRemaining) {
              int toRead =
              cdst.addBuffer(buf);
              bytesRemaining -= buf.readableBytes();
            } else if (buf.readableBytes() == bytesRemaining) {
              cdst.addBuffer(buf);
              bytesRemaining = 0;
              break;
            } else {
              cdst.addBuffer(buf.readBytes(bytesRemaining));
              bytesRemaining = 0;
              state.addRequestedFirst(buf);
              break;
            }
          } while ((buf = state.getRequestedFirst()) != null);
          assert bytesRemaining == 0;
        }
        state.numBytesRead += dst.readableBytes();
        statsTraceCtx.inboundWireSize(dst.readableBytes());
        statsTraceCtx.inboundUncompressedSize(dst.readableBytes());
        listener.messagesAvailable(
            new SingleMessageProducer(ReadableBuffers.openStream(dst, true)));

        return true;
      }
    }
    return false;
  }

  private int slowReadHeader(MessageState state, ReadableBuffer current) {
    /*
    assert !state.deframingBody;
    while (state.headerBytes.remaining() != 0 && current.readableBytes() > 0) {
      headerBytes.put((byte) current.readUnsignedByte());
    }

    if (headerBytes.remaining() == 0) {
      headerBytes.flip();
      int flags = headerBytes.get();
      int length = headerBytes.getInt();
      headerBytes.flip();
      return current;
    }

    if (unprocessed1 != null && (current = unprocessed1.pollFirst()) != null) {
      do {

      } while ((current = unprocessed1.pollFirst()) != null);
    }

    return current;
    */
    return 0;
  }

  private void handleHeader(MessageState state, int flags, int length) {
    if ((flags & RESERVED_MASK) != 0) {
      throw Status.INTERNAL.withDescription(
          debugString + ": Frame header malformed: reserved bits not zero")
          .asRuntimeException();
    }

    // Update the required length to include the length of the frame.
    if (length < 0 || length > maxInboundMessageSize) {
      throw Status.RESOURCE_EXHAUSTED.withDescription(
          String.format("%s: Frame size %d exceeds maximum: %d.",
              debugString, length, maxInboundMessageSize))
          .asRuntimeException();
    }

    statsTraceCtx.inboundMessage();

    state.bodySizeBytes = length;
    state.isCompressed = (flags & COMPRESSED_FLAG_MASK) != 0;
    state.deframingBody = true;
  }

  private void handleBody(MessageState state) {

  }
  /**
   * Processes the GRPC message body, which depending on frame header flags may be compressed.
   */
  /*
  private void processBody() {
    InputStream stream = compressedFlag ? getCompressedBody() : getUncompressedBody();
    nextFrame = null;
    listener.messagesAvailable(new SingleMessageProducer(stream));

    // Done with this frame, begin processing the next header.
    state = State.HEADER;
    requiredLength = HEADER_LENGTH;
  }

  private InputStream getUncompressedBody(MessageState state, ReadableBuffer current) {
    statsTraceCtx.inboundUncompressedSize(state.bodySizeBytes);
    CompositeReadableBuffer dst = new CompositeReadableBuffer();
    if (current.readableBytes() <= state.bodySizeBytes) {
      dst.addBuffer();
    }
    return ReadableBuffers.openStream(nextFrame, true);
  }

  private InputStream getCompressedBody(MessageState state, ReadableBuffer current) {
    if (decompressor == Codec.Identity.NONE) {
      throw Status.INTERNAL.withDescription(
          debugString + ": Can't decode compressed frame as compression not configured.")
          .asRuntimeException();
    }

    try {
      // Enforce the maxMessageSize limit on the returned stream.
      InputStream unlimitedStream =
          decompressor.decompress(ReadableBuffers.openStream(nextFrame, true));
      return new SizeEnforcingInputStream(
          unlimitedStream, maxInboundMessageSize, statsTraceCtx, debugString);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
*/
  /**
   * An {@link InputStream} that enforces the {@link #maxMessageSize} limit for compressed frames.
   */
  @VisibleForTesting
  static final class SizeEnforcingInputStream extends FilterInputStream {
    private final int maxMessageSize;
    private final StatsTraceContext statsTraceCtx;
    private final String debugString;
    private long maxCount;
    private long count;
    private long mark = -1;

    SizeEnforcingInputStream(InputStream in, int maxMessageSize, StatsTraceContext statsTraceCtx,
        String debugString) {
      super(in);
      this.maxMessageSize = maxMessageSize;
      this.statsTraceCtx = statsTraceCtx;
      this.debugString = debugString;
    }

    @Override
    public int read() throws IOException {
      int result = in.read();
      if (result != -1) {
        count++;
      }
      verifySize();
      reportCount();
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int result = in.read(b, off, len);
      if (result != -1) {
        count += result;
      }
      verifySize();
      reportCount();
      return result;
    }

    @Override
    public long skip(long n) throws IOException {
      long result = in.skip(n);
      count += result;
      verifySize();
      reportCount();
      return result;
    }

    @Override
    public synchronized void mark(int readlimit) {
      in.mark(readlimit);
      mark = count;
      // it's okay to mark even if mark isn't supported, as reset won't work
    }

    @Override
    public synchronized void reset() throws IOException {
      if (!in.markSupported()) {
        throw new IOException("Mark not supported");
      }
      if (mark == -1) {
        throw new IOException("Mark not set");
      }

      in.reset();
      count = mark;
    }

    private void reportCount() {
      if (count > maxCount) {
        statsTraceCtx.inboundUncompressedSize(count - maxCount);
        maxCount = count;
      }
    }

    private void verifySize() {
      if (count > maxMessageSize) {
        throw Status.RESOURCE_EXHAUSTED.withDescription(String.format(
            "%s: Compressed frame exceeds maximum frame size: %d. Bytes read: %d. ",
            debugString, maxMessageSize, count)).asRuntimeException();
      }
    }
  }

  private static class SingleMessageProducer implements StreamListener.MessageProducer {
    private InputStream message;

    private SingleMessageProducer(InputStream message) {
      this.message = message;
    }

    @Nullable
    @Override
    public InputStream next() {
      InputStream messageToReturn = message;
      message = null;
      return messageToReturn;
    }
  }
}
