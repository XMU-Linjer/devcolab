package com.devcollab.knowledgecore.common.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(RedisCoordinationProperties.class)
public class RedisCoordinationConfig implements WebMvcConfigurer {

    private final UserOperationRateLimitInterceptor rateLimitInterceptor;

    public RedisCoordinationConfig(
            UserOperationRateLimitInterceptor rateLimitInterceptor
    ) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
