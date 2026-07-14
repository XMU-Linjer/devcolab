package com.devcollab.knowledgecore.search.projection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ElasticsearchSearchProperties.class)
public class SearchProjectionConfiguration {
}
