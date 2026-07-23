package com.devcollab.mcp.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "devcollab.mcp")
public record McpProperties(
        @NotBlank String endpoint,
        @NotBlank String serverName,
        @NotBlank String serverVersion,
        @Min(1) @Max(10_000) int maxCodeLines,
        @Min(1) int maxOutputCharacters,
        @Min(1) int maxPathCharacters,
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @NotEmpty List<@NotBlank String> allowedHosts,
        URI coreBaseUrl,
        Duration coreTimeout
) {
}
