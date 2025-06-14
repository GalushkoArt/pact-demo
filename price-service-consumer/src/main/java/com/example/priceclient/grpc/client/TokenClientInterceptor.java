package com.example.priceclient.grpc.client;

import io.grpc.*;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * Client interceptor that adds an Authorization header for gRPC calls.
 */
@GrpcGlobalClientInterceptor
public class TokenClientInterceptor implements ClientInterceptor {

    private final String token;

    public TokenClientInterceptor(@Value("${grpc.auth.token}") String token) {
        this.token = token;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
                super.start(responseListener, headers);
            }
        };
    }
}
