package com.devcollab.knowledgecore.auth.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenService {

    private final JwtProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.secret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(properties.issuer())
                .build();
    }

    public String issueAccessToken(UUID userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        return JWT.create()
                .withIssuer(properties.issuer())
                .withSubject(userId.toString())
                .withClaim("username", username)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    public TokenClaims verify(String token) {
        DecodedJWT jwt = verifier.verify(token);

        return new TokenClaims(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaim("username").asString(),
                jwt.getExpiresAt().toInstant()
        );
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public record TokenClaims(
            UUID userId,
            String username,
            Instant expiresAt
    ) {
    }
}
