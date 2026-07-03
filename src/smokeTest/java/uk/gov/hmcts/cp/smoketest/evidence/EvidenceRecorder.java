package uk.gov.hmcts.cp.smoketest.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EvidenceRecorder {

    private static final Path EVIDENCE_DIR = Path.of("build", "smoke-evidence");

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void record(final String scenarioName, final String caseUrn, final Map<String, Object> details) {
        try {
            Files.createDirectories(EVIDENCE_DIR);
            final Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("scenario", scenarioName);
            evidence.put("caseUrn", caseUrn);
            evidence.put("recordedAt", Instant.now().toString());
            evidence.putAll(details);

            final Path file = EVIDENCE_DIR.resolve(scenarioName + "-" + caseUrn + ".json");
            objectMapper.writeValue(file.toFile(), evidence);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}