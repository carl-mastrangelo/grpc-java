package io.grpc;

import static io.grpc.DecompressorRegistry.getAdvertisedMessageEncodings;
import static io.grpc.DecompressorRegistry.getKnownMessageEncodings;
import static io.grpc.DecompressorRegistry.lookupDecompressor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link DecompressorRegistry}.
 */
@RunWith(JUnit4.class)
public class MessageEncodingTest {

  private final Dummy dummyDecompressor = new Dummy();

  @Before
  public void setUp() {
    DecompressorRegistry.resetDecompressors();
  }

  @Test
  public void lookupDecompressor_checkDefaultMessageEncodingsExist() {
    // Explicitly put the names in, rather than link against MessageEncoding
    assertNotNull("Expected identity to be registered", lookupDecompressor("identity"));
    assertNotNull("Expected gzip to be registered", lookupDecompressor("gzip"));
  }

  @Test
  public void getKnownMessageEncodings_checkDefaultMessageEncodingsExist() {
    Set<String> knownEncodings = new HashSet<String>();
    knownEncodings.add("identity");
    knownEncodings.add("gzip");

    assertEquals(knownEncodings, getKnownMessageEncodings());
  }

  /*
   * This test will likely change once encoders are advertised
   */
  @Test
  public void getAdvertisedMessageEncodings_noEncodingsAdvertised() {
    assertTrue(getAdvertisedMessageEncodings().isEmpty());
  }

  @Test
  public void registerDecompressor_advertisedDecompressor() {
    DecompressorRegistry.register(dummyDecompressor, true);

    assertTrue(getAdvertisedMessageEncodings().contains(dummyDecompressor.getMessageEncoding()));
  }

  @Test
  public void registerDecompressor_nonadvertisedDecompressor() {
    DecompressorRegistry.register(dummyDecompressor, false);

    assertFalse(getAdvertisedMessageEncodings().contains(dummyDecompressor.getMessageEncoding()));
  }

  private static final class Dummy implements Decompressor {
    @Override
    public String getMessageEncoding() {
      return "dummy";
    }

    @Override
    public InputStream decompress(InputStream is) throws IOException {
      return is;
    }
  }
}

