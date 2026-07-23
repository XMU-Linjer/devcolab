package com.devcollab.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class KnowledgeCoreClientConfig {

    @Bean
    RestClient knowledgeCoreRestClient(McpProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.coreTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.coreTimeout());
        return RestClient.builder()
                .baseUrl(properties.coreBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
