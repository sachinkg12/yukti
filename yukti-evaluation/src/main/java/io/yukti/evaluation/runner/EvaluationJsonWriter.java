package io.yukti.evaluation.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Writes evaluation results to JSON for downstream analysis scripts.
 * Open Closed: format change requires only this writer; producers do not change.
 *
 * <p>Note: Jackson's default ObjectMapper cannot serialize {@link Instant}. Rather
 * than pull in the jsr310 dependency, we register a custom serializer inline that
 * converts {@link Instant} to ISO-8601 string. This keeps the module dependency
 * surface minimal.
 */
public final class EvaluationJsonWriter {

    private static final ObjectMapper OM = createMapper();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        var module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Instant.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator gen,
                                  com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                gen.writeString(value.toString());
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    public void writeResults(EvaluationResults results, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        OM.writeValue(outputFile.toFile(), results);
    }

    public void writeSummary(java.util.List<EvaluationSummaryRow> rows, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        OM.writeValue(outputFile.toFile(), rows);
    }
}
