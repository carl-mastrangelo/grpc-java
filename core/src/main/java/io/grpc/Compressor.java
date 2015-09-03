package io.grpc;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents a message compressor.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
public interface Compressor {
  /**
   * Returns the message encoding that this compressor uses.
   *
   * <p>This can be values such as "gzip", "deflate", "snappy", etc.
   */
  String getMessageEncoding();

  /**
   * Wraps an existing output stream with a compressing output stream.
   * @param os The output stream of uncompressed data
   * @return An output stream that compresses
   */
  OutputStream compress(OutputStream os) throws IOException;
}

