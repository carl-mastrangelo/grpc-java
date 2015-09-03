package io.grpc;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encloses classes related to the compression and decompression of messages.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
public interface Codec extends Compressor, Decompressor {
  /**
   * A gzip compressor and decompressor.  In the future this will likely support other
   * compression methods, such as compression level.
   */
  @ExperimentalApi("https://github.com/grpc/grpc-java/issues/492") class Gzip implements Codec {
    @Override
    public String getMessageEncoding() {
      return "gzip";
    }
  
    @Override
    public OutputStream compress(OutputStream os) throws IOException {
      return new GZIPOutputStream(os);
    }
  
    @Override
    public InputStream decompress(InputStream is) throws IOException {
      return new GZIPInputStream(is);
    }
  }


  /**
   * Special sentinel codec indicating that no compression should be used.  Users should use
   * reference equality to see if compression is disabled.
   */
  @ExperimentalApi
  public static final Codec NONE = new Codec() {
    @Override
    public InputStream decompress(InputStream is) throws IOException {
      return is;
    }

    @Override
    public String getMessageEncoding() {
      return "identity";
    }

    @Override
    public OutputStream compress(OutputStream os) throws IOException {
      return os;
    }
  };
}
