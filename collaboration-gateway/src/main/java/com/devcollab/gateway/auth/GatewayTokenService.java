package com.devcollab.gateway.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class GatewayTokenService {

    private final JWTVerifier verifier;

    public GatewayTokenService(GatewayJwtProperties properties) {
        Algorithm algorithm = Algorithm.HMAC256(properties.secret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(properties.issuer())
                .withAudience(properties.audience())
                .build();
    }

    public GatewayUser verify(String token) {
        DecodedJWT jwt = verifier.verify(token);
        return new GatewayUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaim("username").asString(),
                UUID.fromString(jwt.getClaim("sid").asString()),
                jwt.getExpiresAt().toInstant()
        );
    }

    public record GatewayUser(
            UUID userId,
            String username,
            UUID sessionId,
            Instant expiresAt
    ) {
    }
}
