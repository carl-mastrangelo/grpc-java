/*
 * Copyright 2016, Google Inc. All rights reserved.
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

package io.grpc.netty;

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.util.Iterator;
import java.util.Map.Entry;

/**
 * A custom implementation of Http2Headers that only includes methods used by gRPC.
 */
final class GrpcHttp2Headers extends AbstractHttp2Headers {

  private final AsciiString[] normalHeaders;
  private final AsciiString[] psuedoHeaders;
  private static final AsciiString[] EMPTY = new AsciiString[]{};

  static GrpcHttp2Headers clientHeaders(byte[][] serializedMetadata, AsciiString authority,
      AsciiString path, AsciiString method, AsciiString scheme, AsciiString userAgent) {
    AsciiString[] psuedoHeaders = new AsciiString[] {
        Http2Headers.PseudoHeaderName.AUTHORITY.value(), authority,
        Http2Headers.PseudoHeaderName.PATH.value(), path,
        Http2Headers.PseudoHeaderName.METHOD.value(), method,
        Http2Headers.PseudoHeaderName.SCHEME.value(), scheme,
        Utils.CONTENT_TYPE_HEADER, Utils.CONTENT_TYPE_GRPC,
        Utils.TE_HEADER, Utils.TE_TRAILERS,
        Utils.USER_AGENT, userAgent,
    };
    return new GrpcHttp2Headers(psuedoHeaders, serializedMetadata);
  }

  static GrpcHttp2Headers serverHeaders(byte[][] serializedMetadata) {
    AsciiString[] psuedoHeaders = new AsciiString[] {
        Http2Headers.PseudoHeaderName.STATUS.value(), Utils.STATUS_OK,
        Utils.CONTENT_TYPE_HEADER, Utils.CONTENT_TYPE_GRPC,
    };
    return new GrpcHttp2Headers(psuedoHeaders, serializedMetadata);
  }

  static GrpcHttp2Headers serverTrailers(byte[][] serializedMetadata) {
    return new GrpcHttp2Headers(EMPTY, serializedMetadata);
  }

  private GrpcHttp2Headers(AsciiString[] psuedoHeaders, byte[][] serializedMetadata) {
    normalHeaders = new AsciiString[serializedMetadata.length];
    for (int i = 0; i < normalHeaders.length; i++) {
      normalHeaders[i] = new AsciiString(serializedMetadata[i], false);
    }
    this.psuedoHeaders = psuedoHeaders;
  }

  @Override
  public Iterator<Entry<CharSequence, CharSequence>> iterator() {
    return new Itr();
  }

  @Override
  public int size() {
    return (normalHeaders.length + psuedoHeaders.length) / 2;
  }

  private class Itr implements Iterator<Entry<CharSequence, CharSequence>> {
    private int idx;
    private AsciiString[] currentArray;

    @Override
    public boolean hasNext() {
      if (currentArray == null) {
        currentArray = psuedoHeaders;
      }
      if (currentArray == psuedoHeaders) {
        if (idx < psuedoHeaders.length) {
          return true;
        } else {
          currentArray = normalHeaders;
          idx = 0;
        }
      }

      return idx < normalHeaders.length;
    }

    @Override
    public Entry<CharSequence, CharSequence> next() {
      final AsciiString key = currentArray[idx];
      final AsciiString value = currentArray[idx + 1];
      idx += 2;
      return new Entry<CharSequence, CharSequence>() {
        @Override
        public CharSequence setValue(CharSequence value) {
          throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence getValue() {
          return value;
        }

        @Override
        public CharSequence getKey() {
          return key;
        }
      };
    }
  }
}
