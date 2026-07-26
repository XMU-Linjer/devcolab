package com.devcollab.mcp.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "devcollab.mcp.review-submission")
public record ReviewSubmissionProperties(
        @Min(1) int maxOperations,
        @Min(1) int maxEvidence,
        @Min(1) int maxSummaryCharacters,
        @Min(1) int maxRationaleCharacters,
        @Min(1) int maxProposedCharacters,
        @Min(1) int maxDescriptionCharacters
) {
}
