package uk.gov.hmcts.cp.smoketest.fixtures;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SpiInMessageBuilder {

    private static final String TEMPLATE_RESOURCE = "spi-in-minimal.xml";
    private static final DateTimeFormatter HEARING_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter URN_YEAR_FORMAT = DateTimeFormatter.ofPattern("yy");

    public SpiInFixture build() {
        final String urn = generateUrn();
        final String requestId = UUID.randomUUID().toString();
        final String hearingDate = LocalDate.now().plusDays(4).format(HEARING_DATE_FORMAT);
        final String addressLine1 = "1 Smoke Test Street";

        final String soapEnvelope = readTemplate()
                .replace("REQUEST_ID", requestId)
                .replace("NEW_URN", urn)
                .replace("TODAY+4", hearingDate)
                .replace("ADDRESS_LINE1", addressLine1);

        return new SpiInFixture(urn, soapEnvelope);
    }

    private String generateUrn() {
        final int unit = ThreadLocalRandom.current().nextInt(10, 99);
        final int number = ThreadLocalRandom.current().nextInt(10_000, 99_999);
        final String year = LocalDate.now().format(URN_YEAR_FORMAT);
        return unit + "GD" + number + year;
    }

    private String readTemplate() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(TEMPLATE_RESOURCE + " not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record SpiInFixture(String urn, String soapEnvelope) {
    }
}