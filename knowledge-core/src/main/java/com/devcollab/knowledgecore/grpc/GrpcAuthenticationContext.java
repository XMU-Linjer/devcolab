package com.devcollab.knowledgecore.grpc;

import com.devcollab.knowledgecore.auth.infrastructure.JwtTokenService.TokenClaims;
import io.grpc.Context;

public final class GrpcAuthenticationContext {

    static final Context.Key<TokenClaims> TOKEN_CLAIMS =
            Context.key("devcollab-token-claims");

    private GrpcAuthenticationContext() {
    }

    public static TokenClaims requireClaims() {
        TokenClaims claims = TOKEN_CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException(
                    "Authenticated gRPC user is unavailable"
            );
        }
        return claims;
    }
}
