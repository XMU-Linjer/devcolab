package com.devcollab.gateway.collaboration;

import com.devcollab.protocol.core.v1.KnowledgeCoreCollaborationServiceGrpc;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class GrpcCoreClientSupport {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization",
            Metadata.ASCII_STRING_MARSHALLER
    );
    private static final Metadata.Key<String> ERROR_CODE = Metadata.Key.of(
            "devcollab-error-code",
            Metadata.ASCII_STRING_MARSHALLER
    );

    private GrpcCoreClientSupport() {
    }

    static KnowledgeCoreCollaborationServiceGrpc
            .KnowledgeCoreCollaborationServiceBlockingStub authenticatedStub(
            CoreGrpcChannel channel,
            Duration deadline,
            String accessToken
    ) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, "Bearer " + accessToken);
        return KnowledgeCoreCollaborationServiceGrpc.newBlockingStub(
                        channel.channel()
                )
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(
                        metadata
                ))
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    static boolean isConflict(StatusRuntimeException exception) {
        return exception.getStatus().getCode() == Status.Code.ABORTED
                || exception.getStatus().getCode()
                == Status.Code.ALREADY_EXISTS;
    }

    static boolean isRejected(StatusRuntimeException exception) {
        return switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT,
                 NOT_FOUND,
                 PERMISSION_DENIED,
                 UNAUTHENTICATED,
                 FAILED_PRECONDITION,
                 RESOURCE_EXHAUSTED -> true;
            default -> false;
        };
    }

    static String errorMessage(StatusRuntimeException exception) {
        Metadata trailers = exception.getTrailers();
        String errorCode = trailers == null ? null : trailers.get(ERROR_CODE);
        if (errorCode != null && !errorCode.isBlank()) {
            return "Core rejected operation: " + errorCode;
        }
        String description = exception.getStatus().getDescription();
        return description == null || description.isBlank()
                ? "Core gRPC request failed: "
                + exception.getStatus().getCode()
                : description;
    }
}
