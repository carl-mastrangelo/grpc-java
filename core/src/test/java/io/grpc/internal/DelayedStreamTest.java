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

package io.grpc.internal;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;

import io.grpc.CallOptions;
import io.grpc.Codec;
import io.grpc.DecompressorRegistry;
import io.grpc.IntegerMarshaller;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;

/**
 * Tests for {@link DelayedStream}.  Most of the state checking is enforced by
 * {@link ClientCallImpl} so we don't check it here.
 */
@RunWith(JUnit4.class)
public class DelayedStreamTest {
  private static final Executor executor = MoreExecutors.directExecutor();

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Mock private ClientStreamListener listener;
  @Mock private ClientTransport transport;
  @Mock private ClientStream realStream;
  @Captor private ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
  private DelayedStream stream;
  private Metadata headers = new Metadata();

  private MethodDescriptor<Integer, Integer> method = MethodDescriptor.create(
      MethodType.UNARY, "service/method", new IntegerMarshaller(), new IntegerMarshaller());

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    stream = new DelayedStream(CallOptions.DEFAULT, headers, listener, executor, method);
  }

  @Test
  public void realStreamCreatedWhenTransportReady() {
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);

    stream.createStream(transport);

    verify(transport).newStream(method, headers, listener);
  }

  @Test
  public void createStream_sendsAllMessages() {
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);
    stream.setCompressor(Codec.Identity.NONE);

    DecompressorRegistry registry = DecompressorRegistry.newEmptyInstance();
    stream.setDecompressionRegistry(registry);

    stream.setMessageCompression(true);
    InputStream message = new ByteArrayInputStream(new byte[]{'a'});
    stream.writeMessage(message);
    stream.setMessageCompression(false);
    stream.writeMessage(message);

    stream.createStream(transport);


    verify(transport).newStream(method, headers, listener);
    verify(realStream).setCompressor(Codec.Identity.NONE);
    verify(realStream).setDecompressionRegistry(registry);

    // Verify that the order was correct, even though they should be interleaved with the
    // writeMessage calls
    verify(realStream).setMessageCompression(true);
    verify(realStream, times(2)).setMessageCompression(false);

    verify(realStream, times(2)).writeMessage(message);
  }

  @Test
  public void createStream_halfClose() {
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);
    stream.halfClose();
    stream.createStream(transport);

    verify(realStream).halfClose();
  }

  @Test
  public void createStream_flush() {
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);
    stream.flush();
    stream.createStream(transport);

    verify(realStream).flush();
  }

  @Test
  public void createStream_flowControl() {
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);
    stream.request(1);
    stream.request(2);

    stream.createStream(transport);

    verify(realStream).request(3);
  }

  @Test
  public void createStream_cantCreateTwice() {
    // The first call will be a success
    when(transport.newStream(
        isA(MethodDescriptor.class),
        isA(Metadata.class),
        isA(ClientStreamListener.class)))
        .thenReturn(realStream);
    stream.createStream(transport);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Stream already created");

    stream.createStream(transport);
  }

  @Test
  public void streamCancelled() {
    stream.cancel(Status.CANCELLED);

    // Should be a no op, and not fail due to transport not returning a newStream
    stream.createStream(transport);

    verify(listener).closed(eq(Status.CANCELLED), isA(Metadata.class));
  }

  @Test
  public void createStream_deadlineExceeded() {
    CallOptions callOptions = CallOptions.DEFAULT.withDeadlineNanoTime(System.nanoTime() - 1);
    headers.put(GrpcUtil.TIMEOUT_KEY, 1L);

    stream = new DelayedStream(callOptions, headers, listener, executor, method);
    stream.createStream(transport);

    verify(listener).closed(eq(Status.DEADLINE_EXCEEDED), isA(Metadata.class));
  }
}

