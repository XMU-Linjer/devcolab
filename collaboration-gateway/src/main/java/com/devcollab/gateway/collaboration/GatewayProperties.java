package com.devcollab.gateway.collaboration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.gateway")
public record GatewayProperties(
        String coreBaseUrl,
        Duration presenceTtl,
        Duration editingTtl,
        Duration operationDedupTtl
) {

    public GatewayProperties {
        if (coreBaseUrl == null || coreBaseUrl.isBlank()) {
            throw new IllegalArgumentException("core-base-url 不能为空");
        }
        if (presenceTtl == null || presenceTtl.isNegative() || presenceTtl.isZero()) {
            throw new IllegalArgumentException("presence-ttl 必须大于 0");
        }
        if (editingTtl == null || editingTtl.isNegative() || editingTtl.isZero()) {
            throw new IllegalArgumentException("editing-ttl 必须大于 0");
        }
        if (operationDedupTtl == null
                || operationDedupTtl.isNegative()
                || operationDedupTtl.isZero()) {
            throw new IllegalArgumentException("operation-dedup-ttl 必须大于 0");
        }
    }
}
