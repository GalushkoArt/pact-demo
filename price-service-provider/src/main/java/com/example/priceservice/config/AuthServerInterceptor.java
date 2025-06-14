package com.example.priceservice.config;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * Simple gRPC authentication interceptor verifying an Authorization token.
 */
@Slf4j
@GrpcGlobalServerInterceptor
public class AuthServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTH_CHECKED =
            Metadata.Key.of("x-authenticated", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.token:valid-token}")
    private String validToken;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String auth = headers.get(AUTHORIZATION);
        if (auth == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (("Bearer " + validToken).equals(auth)) {
            ServerCall<ReqT, RespT> forwarding = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void sendHeaders(Metadata responseHeaders) {
                    responseHeaders.put(AUTH_CHECKED, "true");
                    super.sendHeaders(responseHeaders);
                }
            };
            return next.startCall(forwarding, headers);
        }
        call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
