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

package io.grpc.protobuf.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.protobuf.ByteString;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.reflection.testing.AnotherDynamicServiceGrpc;
import io.grpc.reflection.testing.DynamicReflectionTestDepthTwoProto;
import io.grpc.reflection.testing.DynamicServiceGrpc;
import io.grpc.reflection.testing.ReflectableServiceGrpc;
import io.grpc.reflection.testing.ReflectionTestDepthThreeProto;
import io.grpc.reflection.testing.ReflectionTestDepthTwoAlternateProto;
import io.grpc.reflection.testing.ReflectionTestDepthTwoProto;
import io.grpc.reflection.testing.ReflectionTestProto;
import io.grpc.reflection.v1alpha.ExtensionNumberResponse;
import io.grpc.reflection.v1alpha.ExtensionRequest;
import io.grpc.reflection.v1alpha.FileDescriptorResponse;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.StreamRecorder;
import io.grpc.util.MutableHandlerRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ProtoReflectionService}. */
@RunWith(JUnit4.class)
public class ProtoReflectionServiceTest {
  private static final String TEST_HOST = "localhost";
  // Set based on the actual root
  private static final String FILE_NAME_PREFIX = "";
  private MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();
  private BindableService reflectionService;
  private ServerServiceDefinition dynamicService =
      (new DynamicServiceGrpc.DynamicServiceImplBase() {}).bindService();
  private ServerServiceDefinition anotherDynamicService =
      (new AnotherDynamicServiceGrpc.AnotherDynamicServiceImplBase() {}).bindService();
  private Server server;
  private ManagedChannel channel;
  private ServerReflectionGrpc.ServerReflectionStub stub;

  @Before
  public void setUp() throws Exception {
    reflectionService = ProtoReflectionService.getInstance();
    server =
        InProcessServerBuilder.forName("proto-reflection-test")
            .directExecutor()
            .addService(reflectionService)
            .addService(new ReflectableServiceGrpc.ReflectableServiceImplBase() {})
            .fallbackHandlerRegistry(handlerRegistry)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName("proto-reflection-test").directExecutor().build();
    stub = ServerReflectionGrpc.newStub(channel);
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.shutdownNow();
    }
    if (channel != null) {
      channel.shutdownNow();
    }
  }

  @Test
  public void listServices() throws Exception {
    Set<ServiceResponse> originalServices =
        new HashSet<ServiceResponse>(
            Arrays.asList(
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.v1alpha.ServerReflection")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.ReflectableService")
                    .build()));
    assertServiceResponseEquals(originalServices);

    handlerRegistry.addService(dynamicService);
    assertServiceResponseEquals(
        new HashSet<ServiceResponse>(
            Arrays.asList(
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.v1alpha.ServerReflection")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.ReflectableService")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.DynamicService")
                    .build())));

    handlerRegistry.addService(anotherDynamicService);
    assertServiceResponseEquals(
        new HashSet<ServiceResponse>(
            Arrays.asList(
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.v1alpha.ServerReflection")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.ReflectableService")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.DynamicService")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.AnotherDynamicService")
                    .build())));

    handlerRegistry.removeService(dynamicService);
    assertServiceResponseEquals(
        new HashSet<ServiceResponse>(
            Arrays.asList(
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.v1alpha.ServerReflection")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.ReflectableService")
                    .build(),
                ServiceResponse.newBuilder()
                    .setName("grpc.reflection.testing.AnotherDynamicService")
                    .build())));

    handlerRegistry.removeService(anotherDynamicService);
    assertServiceResponseEquals(originalServices);
  }

  @Test
  public void fileByFilename() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileByFilename(
                FILE_NAME_PREFIX + "io/grpc/reflection/testing/reflection_test_depth_three.proto")
            .build();

    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    assertEquals(goldenResponse, responseObserver.firstValue().get());
  }

  @Test
  public void fileByFilenameForMutableServices() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileByFilename(
                FILE_NAME_PREFIX
                    + "io/grpc/reflection/testing/dynamic_reflection_test_depth_two.proto")
            .build();
    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        DynamicReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    handlerRegistry.addService(dynamicService);
    requestObserver.onNext(request);
    handlerRegistry.removeService(dynamicService);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    assertEquals(goldenResponse, responseObserver.getValues().get(0));
    assertEquals(
        ServerReflectionResponse.MessageResponseCase.ERROR_RESPONSE,
        responseObserver.getValues().get(1).getMessageResponseCase());
  }

  @Test
  public void fileContainingSymbol() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingSymbol("grpc.reflection.testing.ReflectableService.Method")
            .build();

    List<ByteString> goldenResponse =
        Arrays.asList(
            ReflectionTestProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthTwoAlternateProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString());

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    List<ByteString> response =
        responseObserver
            .firstValue()
            .get()
            .getFileDescriptorResponse()
            .getFileDescriptorProtoList();
    assertEquals(goldenResponse.size(), response.size());
    assertEquals(new HashSet<ByteString>(goldenResponse), new HashSet<ByteString>(response));
  }

  @Test
  public void fileContainingNestedSymbol() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingSymbol("grpc.reflection.testing.NestedTypeOuter.Middle.Inner")
            .build();

    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();
    assertEquals(goldenResponse, responseObserver.firstValue().get());
  }

  @Test
  public void fileContainingSymbolForMutableServices() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingSymbol("grpc.reflection.testing.DynamicRequest")
            .build();
    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        DynamicReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    handlerRegistry.addService(dynamicService);
    requestObserver.onNext(request);
    handlerRegistry.removeService(dynamicService);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    assertEquals(goldenResponse, responseObserver.getValues().get(0));
    assertEquals(
        ServerReflectionResponse.MessageResponseCase.ERROR_RESPONSE,
        responseObserver.getValues().get(1).getMessageResponseCase());
  }

  @Test
  public void fileContainingExtension() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingExtension(
                ExtensionRequest.newBuilder()
                    .setContainingType("grpc.reflection.testing.ThirdLevelType")
                    .setExtensionNumber(100)
                    .build())
            .build();

    List<ByteString> goldenResponse =
        Arrays.asList(
            ReflectionTestProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthTwoAlternateProto.getDescriptor().toProto().toByteString(),
            ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString());

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    List<ByteString> response =
        responseObserver
            .firstValue()
            .get()
            .getFileDescriptorResponse()
            .getFileDescriptorProtoList();
    assertEquals(goldenResponse.size(), response.size());
    assertEquals(new HashSet<ByteString>(goldenResponse), new HashSet<ByteString>(response));
  }

  @Test
  public void fileContainingNestedExtension() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingExtension(
                ExtensionRequest.newBuilder()
                    .setContainingType("grpc.reflection.testing.ThirdLevelType")
                    .setExtensionNumber(101)
                    .build())
            .build();

    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        ReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString())
                    .addFileDescriptorProto(
                        ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();
    assertEquals(goldenResponse, responseObserver.firstValue().get());
  }

  @Test
  public void fileContainingExtensionForMutableServices() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setFileContainingExtension(
                ExtensionRequest.newBuilder()
                    .setContainingType("grpc.reflection.testing.TypeWithExtensions")
                    .setExtensionNumber(200)
                    .build())
            .build();
    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setFileDescriptorResponse(
                FileDescriptorResponse.newBuilder()
                    .addFileDescriptorProto(
                        DynamicReflectionTestDepthTwoProto.getDescriptor().toProto().toByteString())
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    handlerRegistry.addService(dynamicService);
    requestObserver.onNext(request);
    handlerRegistry.removeService(dynamicService);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    assertEquals(goldenResponse, responseObserver.getValues().get(0));
    assertEquals(
        ServerReflectionResponse.MessageResponseCase.ERROR_RESPONSE,
        responseObserver.getValues().get(1).getMessageResponseCase());
  }

  @Test
  public void allExtensionNumbersOfType() throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setAllExtensionNumbersOfType("grpc.reflection.testing.ThirdLevelType")
            .build();

    Set<Integer> goldenResponse = new HashSet<Integer>(Arrays.asList(100, 101));

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();
    Set<Integer> extensionNumberResponseSet =
        new HashSet<Integer>(
            responseObserver
                .firstValue()
                .get()
                .getAllExtensionNumbersResponse()
                .getExtensionNumberList());
    assertEquals(goldenResponse, extensionNumberResponseSet);
  }

  @Test
  public void allExtensionNumbersOfTypeForMutableServices() throws Exception {
    String type = "grpc.reflection.testing.TypeWithExtensions";
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder()
            .setHost(TEST_HOST)
            .setAllExtensionNumbersOfType(type)
            .build();
    ServerReflectionResponse goldenResponse =
        ServerReflectionResponse.newBuilder()
            .setValidHost(TEST_HOST)
            .setOriginalRequest(request)
            .setAllExtensionNumbersResponse(
                ExtensionNumberResponse.newBuilder()
                    .setBaseTypeName(type)
                    .addExtensionNumber(200)
                    .build())
            .build();

    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    handlerRegistry.addService(dynamicService);
    requestObserver.onNext(request);
    handlerRegistry.removeService(dynamicService);
    requestObserver.onNext(request);
    requestObserver.onCompleted();

    assertEquals(goldenResponse, responseObserver.getValues().get(0));
    assertEquals(
        ServerReflectionResponse.MessageResponseCase.ERROR_RESPONSE,
        responseObserver.getValues().get(1).getMessageResponseCase());
  }

  @Test
  public void flowControl() throws Exception {
    FlowControlClientResponseObserver clientResponseObserver =
        new FlowControlClientResponseObserver();
    ClientCallStreamObserver<ServerReflectionRequest> requestObserver =
        (ClientCallStreamObserver<ServerReflectionRequest>)
            stub.serverReflectionInfo(clientResponseObserver);

    // ClientCalls.startCall() calls request(1) initially, so we should get an immediate response.
    requestObserver.onNext(flowControlRequest);
    assertEquals(1, clientResponseObserver.getResponses().size());
    assertEquals(flowControlGoldenResponse, clientResponseObserver.getResponses().get(0));

    // Verify we don't receive an additional response until we request it.
    requestObserver.onNext(flowControlRequest);
    assertEquals(1, clientResponseObserver.getResponses().size());

    requestObserver.request(1);
    assertEquals(2, clientResponseObserver.getResponses().size());
    assertEquals(flowControlGoldenResponse, clientResponseObserver.getResponses().get(1));

    requestObserver.onCompleted();
    assertTrue(clientResponseObserver.onCompleteCalled());
  }

  @Test
  public void flowControlOnCompleteWithPendingRequest() throws Exception {
    FlowControlClientResponseObserver clientResponseObserver =
        new FlowControlClientResponseObserver();
    ClientCallStreamObserver<ServerReflectionRequest> requestObserver =
        (ClientCallStreamObserver<ServerReflectionRequest>)
            stub.serverReflectionInfo(clientResponseObserver);

    // ClientCalls.startCall() calls request(1) initially, so make additional request.
    requestObserver.onNext(flowControlRequest);
    requestObserver.onNext(flowControlRequest);
    requestObserver.onCompleted();
    assertEquals(1, clientResponseObserver.getResponses().size());
    assertFalse(clientResponseObserver.onCompleteCalled());

    requestObserver.request(1);
    assertTrue(clientResponseObserver.onCompleteCalled());
    assertEquals(2, clientResponseObserver.getResponses().size());
    assertEquals(flowControlGoldenResponse, clientResponseObserver.getResponses().get(1));
  }

  private final ServerReflectionRequest flowControlRequest =
      ServerReflectionRequest.newBuilder()
          .setHost(TEST_HOST)
          .setFileByFilename(
              FILE_NAME_PREFIX + "io/grpc/reflection/testing/reflection_test_depth_three.proto")
          .build();
  private final ServerReflectionResponse flowControlGoldenResponse =
      ServerReflectionResponse.newBuilder()
          .setValidHost(TEST_HOST)
          .setOriginalRequest(flowControlRequest)
          .setFileDescriptorResponse(
              FileDescriptorResponse.newBuilder()
                  .addFileDescriptorProto(
                      ReflectionTestDepthThreeProto.getDescriptor().toProto().toByteString())
                  .build())
          .build();

  private static class FlowControlClientResponseObserver
      implements ClientResponseObserver<ServerReflectionRequest, ServerReflectionResponse> {
    private final List<ServerReflectionResponse> responses =
        new ArrayList<ServerReflectionResponse>();
    private boolean onCompleteCalled = false;

    @Override
    public void beforeStart(final ClientCallStreamObserver<ServerReflectionRequest> requestStream) {
      requestStream.disableAutoInboundFlowControl();
    }

    @Override
    public void onNext(ServerReflectionResponse value) {
      responses.add(value);
    }

    @Override
    public void onError(Throwable t) {
      fail("onError called");
    }

    @Override
    public void onCompleted() {
      onCompleteCalled = true;
    }

    public List<ServerReflectionResponse> getResponses() {
      return responses;
    }

    public boolean onCompleteCalled() {
      return onCompleteCalled;
    }
  }

  private void assertServiceResponseEquals(Set<ServiceResponse> goldenResponse) throws Exception {
    ServerReflectionRequest request =
        ServerReflectionRequest.newBuilder().setHost(TEST_HOST).setListServices("services").build();
    StreamRecorder<ServerReflectionResponse> responseObserver = StreamRecorder.create();
    StreamObserver<ServerReflectionRequest> requestObserver =
        stub.serverReflectionInfo(responseObserver);
    requestObserver.onNext(request);
    requestObserver.onCompleted();
    List<ServiceResponse> response =
        responseObserver.firstValue().get().getListServicesResponse().getServiceList();
    assertEquals(goldenResponse.size(), response.size());
    assertEquals(goldenResponse, new HashSet<ServiceResponse>(response));
  }
}
