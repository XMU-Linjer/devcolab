package com.devcollab.mcp.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "devcollab.security.jwt")
public record McpJwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank @Size(min = 32) String secret
) {
}
