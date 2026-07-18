package com.devcollab.gateway;

import com.devcollab.gateway.auth.GatewayJwtProperties;
import com.devcollab.gateway.collaboration.GatewayProperties;
import com.devcollab.gateway.collaboration.CoreGrpcClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        GatewayJwtProperties.class,
        GatewayProperties.class,
        CoreGrpcClientProperties.class
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
