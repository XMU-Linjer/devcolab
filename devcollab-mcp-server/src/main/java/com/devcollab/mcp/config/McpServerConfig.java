package com.devcollab.mcp.config;

import com.devcollab.mcp.capability.McpCapabilityRegistry;
import com.devcollab.mcp.security.McpTransportIdentity;
import com.devcollab.mcp.security.McpUserIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration
public class McpServerConfig {

    @Bean
    McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper.copy());
    }

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(
            McpJsonMapper jsonMapper,
            McpProperties properties
    ) {
        DefaultServerTransportSecurityValidator securityValidator =
                DefaultServerTransportSecurityValidator.builder()
                        .allowedOrigins(properties.allowedOrigins())
                        .allowedHosts(properties.allowedHosts())
                        .build();

        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(properties.endpoint())
                .securityValidator(securityValidator)
                .contextExtractor(this::extractIdentity)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider,
            McpProperties properties
    ) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, properties.endpoint());
        registration.setName("devcollabMcpStreamableHttp");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            McpCapabilityRegistry capabilityRegistry,
            McpProperties properties
    ) {
        return McpServer.sync(transportProvider)
                .serverInfo(properties.serverName(), properties.serverVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(capabilityRegistry.tools())
                .resources(capabilityRegistry.resources())
                .build();
    }

    private io.modelcontextprotocol.common.McpTransportContext extractIdentity(HttpServletRequest request) {
        if (request.getUserPrincipal() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof McpUserIdentity identity) {
            return McpTransportIdentity.context(identity);
        }
        return io.modelcontextprotocol.common.McpTransportContext.EMPTY;
    }
}
