package de.ragtractusx.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@code java -jar app.jar --ingest} runs one ingestion and exits (used by the Kubernetes Job/CronJob).
 * Exit code 0 on success, 1 if the run failed or any file failed, 2 if another run held the lock.
 */
@Component
public class IngestCommandRunner implements ApplicationRunner {

    public static final String OPTION = "ingest";
    private static final Logger log = LoggerFactory.getLogger(IngestCommandRunner.class);

    private final IngestionService ingestion;
    private final ApplicationContext context;

    public IngestCommandRunner(IngestionService ingestion, ApplicationContext context) {
        this.ingestion = ingestion;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }
        int exitCode;
        try {
            IngestionReport report = ingestion.ingest();
            exitCode = report.failed() == 0 ? 0 : 1;
        } catch (IngestionService.IngestionInProgressException e) {
            log.warn(e.getMessage());
            exitCode = 2;
        } catch (RuntimeException e) {
            log.error("Ingestion failed", e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> code));
    }
}
