package de.ragtractusx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagTractusxApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagTractusxApplication.class, args);
    }
}
