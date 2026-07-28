package com.devcollab.knowledgecore.agentdelegation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.agent-delegation")
public record AgentDelegationProperties(
        String serviceToken,
        Duration delegationTtl,
        Duration accessTokenTtl
) {
    public AgentDelegationProperties {
        if (serviceToken == null || serviceToken.length() < 32) {
            throw new IllegalArgumentException(
                    "Agent delegation service token must contain at least 32 characters"
            );
        }
        if (delegationTtl == null || delegationTtl.isNegative() || delegationTtl.isZero()) {
            throw new IllegalArgumentException("Agent delegation TTL must be positive");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("Agent access token TTL must be positive");
        }
    }
}
