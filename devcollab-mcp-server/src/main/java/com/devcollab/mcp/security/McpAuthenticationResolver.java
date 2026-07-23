package com.devcollab.mcp.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class McpAuthenticationResolver {

    private final JWTVerifier verifier;

    public McpAuthenticationResolver(McpJwtProperties properties) {
        this.verifier = JWT.require(Algorithm.HMAC256(properties.secret()))
                .withIssuer(properties.issuer())
                .withAudience(properties.audience())
                .build();
    }

    public McpUserIdentity resolve(String accessToken) {
        DecodedJWT jwt = verifier.verify(accessToken);
        String sessionId = jwt.getClaim("sid").asString();
        String username = jwt.getClaim("username").asString();
        if (sessionId == null || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Access token is missing required identity claims");
        }
        return new McpUserIdentity(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(sessionId),
                username,
                accessToken
        );
    }
}
