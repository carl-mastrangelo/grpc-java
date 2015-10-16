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

package io.grpc.netty;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;

import io.grpc.netty.ProtocolNegotiators.TlsChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

@RunWith(JUnit4.class)
public class ProtocolNegotiatorsTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  //@Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private EmbeddedChannel channel = new EmbeddedChannel();

  private ChannelHandlerContext channelHandlerCtx;
  private ChannelPipeline pipeline;
  private SslHandler sslHandler;

  private SSLEngine engine;

  @Before
  public void setUp() throws Exception {
    engine = SSLContext.getDefault().createSSLEngine();
    engine.getSession();

    pipeline = channel.pipeline();
    sslHandler = new SslHandler(engine, false);
    channelHandlerCtx = pipeline.context(sslHandler);

  }

  @Test
  public void tlsHandler() throws Exception {
    ChannelHandler handler = ProtocolNegotiators.serverTls(engine, mock(ChannelHandler.class));

    assertNotNull(handler);
  }

  @Test
  public void tlsHandler_failsOnNullEngine() throws Exception {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("ssl");

    ProtocolNegotiators.serverTls(null, null);
  }

  @Test
  public void tlsAdapter_exceptionClosesContext() throws Exception {
    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(new SslHandler(engine, false), channelHandler);

    handler.exceptionCaught(channelHandlerCtx, new Exception("bad"));

    verify(channelHandlerCtx).close();
  }

  @Test
  public void tlsHandler_handlerAdded() throws Exception {
    SslHandler sslHandler = new SslHandler(engine, false);
    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(sslHandler, channelHandler);
    when(channelHandlerCtx.pipeline()).thenReturn(pipeline);

    handler.handlerAdded(channelHandlerCtx);

    verify(pipeline).addFirst(sslHandler);
  }

  @Test
  public void tlsHandler_userEventTriggeredNonSslEvent() throws Exception {

    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(sslHandler, channelHandler);
    Object nonSslEvent = new Object();

    when(channelHandlerCtx.pipeline()).thenReturn(pipeline);

    handler.userEventTriggered(channelHandlerCtx, nonSslEvent);

    verify(channelHandlerCtx).fireUserEventTriggered(nonSslEvent);
  }

  @Test
  public void tlsHandler_userEventTriggeredSslEvent_unsupportedProtocol() throws Exception {
    SslHandler sslHandler = new SslHandler(engine, false) {
      @Override
      public String applicationProtocol() {
        return "";
      }
    };
    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(sslHandler, channelHandler);
    Object sslEvent = SslHandshakeCompletionEvent.SUCCESS;

    when(channelHandlerCtx.pipeline()).thenReturn(pipeline);
    when(pipeline.get(Matchers.<Class<ChannelHandler>>any())).thenReturn(sslHandler);

    handler.userEventTriggered(channelHandlerCtx, sslEvent);

    verify(channelHandlerCtx).fireUserEventTriggered(sslEvent);
    // No h2 protocol was specified, so this should be closed.
    verify(channelHandlerCtx).close();
  }

  @Test
  public void tlsHandler_userEventTriggeredSslEvent_handshakeFailure() throws Exception {
    SslHandler sslHandler = new SslHandler(engine, false);
    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(sslHandler, channelHandler);
    Object sslEvent = new SslHandshakeCompletionEvent(new RuntimeException("bad"));

    when(channelHandlerCtx.pipeline()).thenReturn(pipeline);
    when(pipeline.get(Matchers.<Class<ChannelHandler>>any())).thenReturn(sslHandler);

    handler.userEventTriggered(channelHandlerCtx, sslEvent);

    verify(channelHandlerCtx).fireUserEventTriggered(sslEvent);
    verify(channelHandlerCtx).close();
  }

  @Test
  public void tlsHandler_userEventTriggeredSslEvent_supportedProtocol() throws Exception {
    SslHandler sslHandler = new SslHandler(engine, false) {
      @Override
      public String applicationProtocol() {
        return Iterables.getFirst(GrpcSslContexts.HTTP2_VERSIONS, "");
      }
    };
    ChannelInboundHandlerAdapter handler =
        new TlsChannelInboundHandlerAdapter(sslHandler, channelHandler);
    Object sslEvent = SslHandshakeCompletionEvent.SUCCESS;

    when(channelHandlerCtx.pipeline()).thenReturn(pipeline);
    when(pipeline.get(Matchers.<Class<ChannelHandler>>any())).thenReturn(sslHandler);

    handler.userEventTriggered(channelHandlerCtx, sslEvent);

    verify(pipeline).replace(handler, null, channelHandler);
    verify(channelHandlerCtx).fireUserEventTriggered(sslEvent);
  }

  @Test
  public void engineLog() {
    Logger logger = Logger.getLogger(ProtocolNegotiators.class.getName());
    Filter oldFilter = logger.getFilter();
    try {
      logger.setFilter(new Filter() {
        @Override
        public boolean isLoggable(LogRecord record) {
          // We still want to the log method to be exercised, just not printed to stderr.
          return false;
        }
      });

      SslHandler sslHandler = new SslHandler(engine, false);

      when(channelHandlerCtx.pipeline()).thenReturn(pipeline);
      when(pipeline.get(Matchers.<Class<ChannelHandler>>any())).thenReturn(sslHandler);

      ProtocolNegotiators.logSslEngineDetails(
          Level.INFO, channelHandlerCtx, "message", new Exception("bad"));
    } finally {
      logger.setFilter(oldFilter);
    }
  }
}
