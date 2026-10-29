package de.ragtractusx;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import de.ragtractusx.ingest.IngestCommandRunner;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagTractusxApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RagTractusxApplication.class);
        if (Arrays.asList(args).contains("--" + IngestCommandRunner.OPTION)) {
            // One-shot ingestion run: no web server needed.
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
