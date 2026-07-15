package com.devcollab.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WorkerApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}
