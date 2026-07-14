package com.devcollab.knowledgecore.search.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devcollab.search.elasticsearch")
public record ElasticsearchSearchProperties(
        boolean enabled,
        String url,
        String indexName
) {
}
