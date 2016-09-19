/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Encloses classes related to the compression and decompression of messages.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1704")
// TODO(carl-mastrangelo): make this not not thread safe.
@ThreadSafe
public final class CompressorRegistry {
  private static final CompressorRegistry DEFAULT_INSTANCE = new CompressorRegistry(
      new Codec.Gzip(),
      Codec.Identity.NONE);

  public static CompressorRegistry getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  public static CompressorRegistry newEmptyInstance() {
    return new CompressorRegistry();
  }

  private final AtomicReference<CompressorInfo[]> compressors =
      new AtomicReference<CompressorInfo[]>();

  @VisibleForTesting
  CompressorRegistry(Compressor ...cs) {
    CompressorInfo[] compressorsInfos = new CompressorInfo[cs.length];
    for (int i = 0; i < cs.length; i++) {
      compressorsInfos[i] = new CompressorInfo(cs[i]);
    }
    compressors.set(compressorsInfos);
  }

  @Nullable
  public Compressor lookupCompressor(String compressorName) {
    CompressorInfo ci = lookupCompressorInfo(compressorName);
    return ci != null ? ci.compressor : null;
  }

  CompressorInfo lookupCompressorInfo(String compressorName) {
    CompressorInfo[] cs = compressors.get();
    for (CompressorInfo ci : cs) {
      if (ci.compressor.getMessageEncoding().equals(compressorName)) {
        return ci;
      }
    }
    return null;
  }

  /**
   * Registers a compressor for both decompression and message encoding negotiation.
   *
   * @param compressor The compressor to register
   */
  public void register(Compressor compressor) {
    String encoding = checkNotNull(compressor.getMessageEncoding(), "message encoding");
    checkArgument(!encoding.isEmpty(), "empty message encoding");
    for (int i = 0; i < encoding.length(); i++) {
      char c = encoding.charAt(i);
      checkArgument(c >= ' ' && c <= '~', "Invalid character: " + c);
      checkArgument(c != ',', "Comma is currently not allowed in message encoding");
    }
    checkArgument(
        encoding.length() == encoding.trim().length(), "Leading/trailing whitespace not allowed");
    checkArgument(!encoding.contains(","), "Comma is currently not allowed in message encoding");

    // None of this would be necessary if this wasn't @ThreadSafe.
    CompressorInfo[] oldCs;
    CompressorInfo[] newCs;
    do {
      oldCs = compressors.get();
      newCs = null;
      for (int i = 0; i < oldCs.length; i++) {
        if (oldCs[i].compressor.getMessageEncoding().equals(encoding)) {
          newCs = Arrays.copyOf(oldCs, oldCs.length);
          newCs[i] = new CompressorInfo(compressor);
          break;
        }
      }
      if (newCs == null) {
        newCs = Arrays.copyOf(oldCs, oldCs.length + 1);
        newCs[oldCs.length] = new CompressorInfo(compressor);
      }

    } while(!compressors.compareAndSet(oldCs, newCs));
  }

  final static class CompressorInfo {
    private final Compressor compressor;
    private final byte[] rawEncoding;

    private CompressorInfo(Compressor compressor) {
      this.compressor = compressor;
      this.rawEncoding = compressor.getMessageEncoding().getBytes(Charsets.US_ASCII);
    }

    Compressor getCompressor() {
      return compressor;
    }

    byte[] getRawEncoding() {
      return rawEncoding;
    }
  }
}
