package io.grpc.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersEncoder;
import io.netty.handler.codec.http2.hpack.Encoder;
import io.netty.util.AsciiString;

final class GrpcHttp2HeadersEncoder implements Http2HeadersEncoder {

  private final Encoder encoder;

  GrpcHttp2HeadersEncoder(int maxHeaderTableSize) {
    encoder = new Encoder(maxHeaderTableSize);
  }

  @Override
  public void encodeHeaders(Http2Headers headers, ByteBuf buffer) throws Http2Exception {
    GrpcHttp2Headers grpcHeaders = (GrpcHttp2Headers) headers;
    for (int i = 0; i < grpcHeaders.preHeaders.length; i += 2) {
      encoder.encodeHeader(out, grpcHeaders.preHeaders[i], grpcHeaders.preHeaders[i+1], false);
    }
    for (int i = 0; i < grpcHeaders.normalHeaders.length; i += 2) {
      encoder.encodeHeader(out, grpcHeaders.normalHeaders[i], grpcHeaders.normalHeaders[i+1], false);
    }
  }

  @Override
  public Configuration configuration() {
    return null;
  }
}

