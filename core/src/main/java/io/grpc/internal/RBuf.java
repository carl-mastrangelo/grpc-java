package io.grpc.internal;

import java.nio.BufferUnderflowException;

public abstract class RBuf {

  public static final class UnlikelyUnderflowException extends BufferUnderflowException {}

  private RBuf next;

  void next(RBuf b) {
    assert next == null;
    next = b;
  }

  protected abstract void close();
  protected abstract int remaining();

  final byte readByte() {
    try {
      return readByteInternal();
    } catch (UnlikelyUnderflowException ignored) {}
    return readByteSlow();
  }

  final byte readByteSlow() {
    return next.readByte();
  }

  final int readInt() {
    try {
      return readIntInternal();
    } catch (UnlikelyUnderflowException ignored) {}
    return readIntSlow();
  }

  private final int readIntSlow() {
    int ret = 0;
    ret |= (readByte() << 24) & 0xFF000000;
    ret |= (readByte() << 16) & 0xFF0000;
    ret |= (readByte() << 8) & 0xFF00;
    ret |= readByte() & 0xFF;
    return ret;
  }

  protected abstract byte readByteInternal() throws UnlikelyUnderflowException;
  protected abstract int readIntInternal() throws UnlikelyUnderflowException;
  protected abstract int readInternal(byte b[], int off, int len) throws UnlikelyUnderflowException;
}
