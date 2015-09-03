package io.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

/**
 * Encloses classes related to the compression and decompression of messages.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
public final class MessageEncoding {

  private static final ConcurrentMap<String, DecompressorInfo> decompressors =
      initializeDefaultDecompressors();

  /**
   * A gzip compressor and decompressor.  In the future this will likely support other
   * compression methods, such as compression level.
   */
  @ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
  public static final class Gzip implements Codec {
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
   * Registers a decompressor for both decompression and message encoding negotiation.
   *
   * @param d The decompressor to register
   * @param advertised If true, the message encoding will be listed in the Accept-Encoding header.
   * @throws IllegalArgumentException if another compressor by the same name is already registered.
   */
  @ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
  public static final void registerDecompressor(Decompressor d, boolean advertised) {
    DecompressorInfo previousInfo =
        decompressors.putIfAbsent(d.getMessageEncoding(), new DecompressorInfo(d, advertised));
    if (previousInfo != null) {
      throw new IllegalArgumentException(
          "A decompressor was already registered: " + previousInfo.decompressor);
    }
  }

  /**
   * Provides a list of all message encodings that have decompressors available.
   */
  @ExperimentalApi
  public static Set<String> getKnownMessageEncodings() {
    return Collections.unmodifiableSet(decompressors.keySet());
  }

  /**
   * Provides a list of all message encodings that have decompressors available and should be
   * advertised.
   */
  @ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
  public static Set<String> getAdvertisedMessageEncodings() {
    Set<String> advertisedDecompressors = new HashSet<String>();
    for (Entry<String, DecompressorInfo> entry : decompressors.entrySet()) {
      if (entry.getValue().advertised) {
        advertisedDecompressors.add(entry.getKey());
      }
    }
    return Collections.unmodifiableSet(advertisedDecompressors);
  }

  /**
   * Returns a decompressor for the given message encoding, or {@code null} if none has been
   * registered.
   *
   * <p>This ignores whether the compressor is advertised.  According to the spec, if we know how
   * to process this encoding, we attempt to, regardless of whether or not it is part of the
   * encodings sent to the remote host.
   */
  @Nullable
  @ExperimentalApi("https://github.com/grpc/grpc-java/issues/492")
  public static Decompressor lookupDecompressor(String messageEncoding) {
    DecompressorInfo info = decompressors.get(messageEncoding);
    return info != null ? info.decompressor : null;
  }

  private MessageEncoding() {
    // construct me not
  }

  private static ConcurrentMap<String, DecompressorInfo> initializeDefaultDecompressors() {
    ConcurrentMap<String, DecompressorInfo> defaultDecompressors =
        new ConcurrentHashMap<String, DecompressorInfo>();
    Decompressor gzip = new Gzip();
    // By default, Gzip
    defaultDecompressors.put(gzip.getMessageEncoding(), new DecompressorInfo(gzip, false));
    defaultDecompressors.put(
        Codec.NONE.getMessageEncoding(), new DecompressorInfo(Codec.NONE, false));
    return defaultDecompressors;
  }

  /**
   * Clears all registered decompressors and resets the registry to the default.  This should only
   * be called from tests.
   */
  @VisibleForTesting
  public static void resetDecompressors() {
    decompressors.clear();
    decompressors.putAll(initializeDefaultDecompressors());
  }

  /**
   * Information about a decompressor.
   */
  private static final class DecompressorInfo {
    private final Decompressor decompressor;
    private volatile boolean advertised;

    DecompressorInfo(Decompressor decompressor, boolean advertised) {
      this.decompressor = checkNotNull(decompressor);
      this.advertised = advertised;
    }
  }
}
