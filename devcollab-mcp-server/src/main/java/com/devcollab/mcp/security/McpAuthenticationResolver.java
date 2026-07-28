package com.devcollab.mcp.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Arrays;
import java.util.Set;

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
        String tokenType = jwt.getClaim("token_type").asString();
        boolean delegated = "agent_delegation".equals(tokenType);
        String[] tools = delegated ? jwt.getClaim("allowed_tools").asArray(String.class) : null;
        return new McpUserIdentity(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(sessionId),
                username,
                accessToken,
                delegated ? tokenType : "user",
                delegated ? uuidClaim(jwt, "workspace_id") : null,
                delegated ? uuidClaim(jwt, "repository_id") : null,
                delegated ? uuidClaim(jwt, "job_id") : null,
                delegated ? requiredClaim(jwt, "revision") : null,
                tools == null ? Set.of() : Set.copyOf(Arrays.asList(tools))
        );
    }

    private UUID uuidClaim(DecodedJWT jwt, String name) {
        return UUID.fromString(requiredClaim(jwt, name));
    }

    private String requiredClaim(DecodedJWT jwt, String name) {
        String value = jwt.getClaim(name).asString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Delegated token is missing required scope claims");
        }
        return value;
    }
}
