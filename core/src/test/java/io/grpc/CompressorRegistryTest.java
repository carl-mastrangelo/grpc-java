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

package io.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link CompressorRegistry}.
 */
@RunWith(JUnit4.class)
public class CompressorRegistryTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private CompressorRegistry registry = CompressorRegistry.newEmptyInstance();

  @Test
  public void lookupCompressor_checkDefaultMessageEncodingsExist() {
    // Explicitly put the names in, rather than link against MessageEncoding
    assertNotNull("Expected identity to be registered",
        CompressorRegistry.getDefaultInstance().lookupCompressor("identity"));
    assertNotNull("Expected gzip to be registered",
        CompressorRegistry.getDefaultInstance().lookupCompressor("gzip"));
  }

  @Test
  public void getKnownMessageEncodings_checkDefaultMessageEncodingsExist() {
    Set<String> knownEncodings = new HashSet<String>();
    knownEncodings.add("identity");
    knownEncodings.add("gzip");

    assertEquals(knownEncodings,
        DecompressorRegistry.getDefaultInstance().getKnownMessageEncodings());
  }

  @Test
  public void registerFailsOnEmptyName() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("empty");

    registry.register(new Dummy() {
      @Override
      public String getMessageEncoding() {
        return "";
      }
    });
  }

  @Test
  public void registerFailsOnNullName() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("message encoding");

    registry.register(new Dummy() {
      @Override
      public String getMessageEncoding() {
        return null;
      }
    });
  }

  @Test
  public void registerFailsOnCommaName() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Comma");

    registry.register(new Dummy() {
      @Override
      public String getMessageEncoding() {
        return ",";
      }
    });
  }

  @Test
  public void registerFailsOnInvalidName() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid character");

    registry.register(new Dummy() {
      @Override
      public String getMessageEncoding() {
        return "\n";
      }
    });
  }

  @Test
  public void registerFailsOnExtraWhitespace() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("whitespace");

    registry.register(new Dummy() {
      @Override
      public String getMessageEncoding() {
        return " gzip ";
      }
    });
  }

  private static class Dummy implements Compressor {

    @Override
    public String getMessageEncoding() {
      return "dummy";
    }

    @Override
    public OutputStream compress(OutputStream os) throws IOException {
      return os;
    }
  }
}

