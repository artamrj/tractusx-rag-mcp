package de.ragtractusx.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import de.ragtractusx.config.RagProperties;
import de.ragtractusx.rag.AnswerResponse;
import de.ragtractusx.rag.AskRequest;
import de.ragtractusx.rag.RagService;
import de.ragtractusx.retrieval.RetrievedChunk;
import de.ragtractusx.retrieval.SearchRequest.Mode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code java -jar app.jar --eval [options]} runs the test set and writes a report, then exits.
 * <pre>
 *   --eval.testset=eval/testset.yaml     test set file
 *   --eval.configs=hybrid:6,vector:6     retrieval configurations to compare (mode:topK)
 *   --eval.retrieval-only                skip answer generation and judging (no Claude cost)
 *   --eval.ids=A01,B03                   only these items        --eval.limit=10   first N items
 *   --eval.concurrency=4                 parallel questions      --eval.out=eval/reports
 *   --eval.input-price=4 --eval.output-price=20   USD per 1M tokens, for the cost column
 * </pre>
 */
@Component
public class EvalRunner implements ApplicationRunner {

    public static final String OPTION = "eval";
    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    record Config(String name, Mode mode, int topK) {
        static Config parse(String spec) {
            String[] parts = spec.trim().split(":");
            Mode mode = Mode.parse(parts[0]);
            int k = parts.length > 1 ? Integer.parseInt(parts[1]) : 6;
            return new Config(mode.tag() + "-k" + k, mode, k);
        }
    }

    private final RagService rag;
    private final JudgeService judge;
    private final ApplicationContext context;
    private final String model;
    private final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    public EvalRunner(RagService rag, JudgeService judge, ApplicationContext context, RagProperties props) {
        this.rag = rag;
        this.judge = judge;
        this.context = context;
        this.model = props.llm().model();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption(OPTION)) {
            return;
        }
        int exit = 0;
        try {
            runEval(args);
        } catch (Exception e) {
            log.error("Evaluation failed", e);
            exit = 1;
        }
        int code = exit;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> code));
    }

    void runEval(ApplicationArguments args) throws Exception {
        Path testset = Path.of(option(args, "eval.testset", "eval/testset.yaml"));
        List<EvalItem> items = TestsetLoader.load(testset);
        String ids = option(args, "eval.ids", "");
        if (!ids.isBlank()) {
            Set<String> wanted = Arrays.stream(ids.split(",")).map(String::trim).collect(Collectors.toSet());
            items = items.stream().filter(i -> wanted.contains(i.id())).toList();
        }
        int limit = Integer.parseInt(option(args, "eval.limit", "0"));
        if (limit > 0 && items.size() > limit) {
            items = items.subList(0, limit);
        }
        boolean retrievalOnly = args.containsOption("eval.retrieval-only");
        int concurrency = Integer.parseInt(option(args, "eval.concurrency", "4"));
        double inPrice = Double.parseDouble(option(args, "eval.input-price", "4"));
        double outPrice = Double.parseDouble(option(args, "eval.output-price", "20"));
        List<Config> configs = Arrays.stream(option(args, "eval.configs", "hybrid:6").split(","))
                .map(Config::parse).toList();

        Path outDir = Path.of(option(args, "eval.out", "eval/reports"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + (retrievalOnly ? "_retrieval" : ""));
        Files.createDirectories(outDir);
        long unreviewed = items.stream().filter(i -> !i.reviewed()).count();
        log.info("Evaluating {} items ({} not reviewed yet) with configs {} -> {}", items.size(), unreviewed,
                configs.stream().map(Config::name).toList(), outDir);

        List<EvalSummary> summaries = new ArrayList<>();
        for (Config config : configs) {
            List<EvalResult> results = runConfig(config, items, retrievalOnly, concurrency);
            EvalSummary summary = EvalSummary.of(config.name(), config.topK(), results, inPrice, outPrice);
            summaries.add(summary);
            json.writeValue(outDir.resolve(config.name() + ".json").toFile(),
                    java.util.Map.of("summary", summary, "results", results));
            log.info("{}: {}", config.name(), summary);
        }
        String markdown = EvalReport.markdown(summaries, items, testset, model, retrievalOnly, unreviewed);
        Files.writeString(outDir.resolve("summary.md"), markdown);
        System.out.println(markdown);
    }

    private List<EvalResult> runConfig(Config config, List<EvalItem> items, boolean retrievalOnly, int concurrency)
            throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            List<Future<EvalResult>> futures = new ArrayList<>();
            for (EvalItem item : items) {
                futures.add(pool.submit(() -> evaluate(config, item, retrievalOnly)));
            }
            List<EvalResult> results = new ArrayList<>();
            for (Future<EvalResult> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    EvalResult evaluate(Config config, EvalItem item, boolean retrievalOnly) {
        try {
            long start = System.nanoTime();
            List<RetrievedChunk> chunks = rag.retrieve(new AskRequest(item.question(), null, config.topK()), config.mode());
            List<String> paths = chunks.stream().map(RetrievedChunk::path).toList();
            RetrievalScores scores = item.answerable() ? RetrievalScores.score(paths, item.expectedSources()) : null;
            if (retrievalOnly) {
                return new EvalResult(item.id(), item.category(), item.lang(), item.answerable(), paths,
                        scores == null ? null : scores.firstRelevantRank(), scores == null ? null : scores.recall(),
                        null, null, null, null, null, null, null, 0, 0, 0, 0, 0, null);
            }
            AnswerResponse answer = rag.generate(item.question(), chunks, start);
            JudgeService.Verdict verdict = judge.judge(item, answer.answer(), chunks);
            log.info("{} [{}] rank={} correctness={} faithfulness={} abstained={}", item.id(), config.name(),
                    scores == null ? "-" : scores.firstRelevantRank(), verdict.correctness(), verdict.faithfulness(),
                    verdict.abstained());
            return new EvalResult(item.id(), item.category(), item.lang(), item.answerable(), paths,
                    scores == null ? null : scores.firstRelevantRank(), scores == null ? null : scores.recall(),
                    answer.answer(), answer.outcome(), verdict.correctness(), verdict.faithfulness(),
                    verdict.citationPrecision(), verdict.abstained(), verdict.comment(), answer.latencyMs(),
                    answer.usage().inputTokens(), answer.usage().outputTokens(),
                    verdict.inputTokens(), verdict.outputTokens(), null);
        } catch (RuntimeException e) {
            log.warn("{} [{}] failed: {}", item.id(), config.name(), e.toString());
            return new EvalResult(item.id(), item.category(), item.lang(), item.answerable(), List.of(), null, null,
                    null, null, null, null, null, null, null, 0, 0, 0, 0, 0, e.toString());
        }
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() || values.getFirst().isBlank() ? fallback : values.getFirst();
    }
}
