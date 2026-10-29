package de.ragtractusx;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import de.ragtractusx.eval.EvalRunner;
import de.ragtractusx.ingest.IngestCommandRunner;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagTractusxApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RagTractusxApplication.class);
        List<String> argList = Arrays.asList(args);
        if (argList.contains("--" + IngestCommandRunner.OPTION) || argList.contains("--" + EvalRunner.OPTION)) {
            // One-shot ingestion or evaluation run: no web server needed.
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
