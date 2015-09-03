package io.grpc;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a message decompressor.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
public interface Decompressor {
  /**
   * Returns the message encoding that this compressor uses.
   *
   * <p>This can be values such as "gzip", "deflate", "snappy", etc.
   */
  String getMessageEncoding();

  /**
   * Wraps an existing input stream with a decompressing input stream.
   * @param is The input stream of uncompressed data
   * @return An input stream that decompresses
   */
  InputStream decompress(InputStream is) throws IOException;
}

