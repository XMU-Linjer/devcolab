package com.devcollab.mcp;

import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.config.ReviewSubmissionProperties;
import com.devcollab.mcp.security.McpJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({
        McpProperties.class,
        McpJwtProperties.class,
        ReviewSubmissionProperties.class
})
public class DevCollabMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevCollabMcpServerApplication.class, args);
    }
}
