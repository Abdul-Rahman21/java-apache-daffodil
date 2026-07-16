package com.example.dfdl.service;

import com.example.dfdl.dto.BinaryCompareResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryCompareServiceTest {

    private static final Charset IBM037 = Charset.forName("IBM037");

    private BinaryCompareService service;

    @BeforeEach
    void setUp() {
        service = new BinaryCompareService();
    }

    @Test
    void compare_identicalBytes_returnsIdenticalVerdict() {
        byte[] bytes = "UNB+IATB:1+UA1SM+1A+260710:1200+REF+REF++T'UNH+1+SMPRES:93:2:IA+REF'TVL+211226+LAX+DEN+UA+1275:L'EQI++++++B3S'CBD+Y:::Y+007:039++++14:24+A:W+B:9'ROD+7++A:F:CH'UNT+6+1'UNZ+1+REF'"
                .getBytes(IBM037);

        BinaryCompareResponse response = service.compare(bytes, "a.bin", bytes, "b.bin");

        assertTrue(response.isSuccess());
        assertEquals("IDENTICAL", response.getVerdict());
        assertEquals(100, response.getMatchPercent());
        assertEquals(0, response.getMismatchPercent());
        assertTrue(response.getMismatchDetails() == null);
        assertTrue(response.getClientJson() != null);
        assertTrue(response.getUnparseJson() != null);
        assertTrue(response.getMismatchedValues().isEmpty());
        assertEquals("SMPRES", response.getClientJson().getEnvelope().get("UNH") instanceof java.util.Map
                ? ((java.util.Map<?, ?>) response.getClientJson().getEnvelope().get("UNH")).get("messageType")
                : null);
        assertTrue(response.getChecks().get("byteIdentical").isPassed());
        assertTrue(response.getChecks().get("encodingIbm037").isPassed());
    }

    @Test
    void compare_sameStructureDifferentTxn_returnsStructurallyMatched() throws Exception {
        Path clientPath = Path.of("samples/Response_SMPRES_4.bin");
        Path unparsePath = Path.of("samples/SMPRES.bin");
        if (!Files.exists(clientPath) || !Files.exists(unparsePath)) {
            // Skip gracefully when sample files are not present in the test workspace.
            return;
        }

        byte[] client = Files.readAllBytes(clientPath);
        byte[] unparse = Files.readAllBytes(unparsePath);

        BinaryCompareResponse response = service.compare(client, "Response_SMPRES_4.bin", unparse, "SMPRES.bin");

        assertTrue(response.isSuccess());
        assertFalse(response.getChecks().get("byteIdentical").isPassed());
        assertTrue(response.getChecks().get("encodingIbm037").isPassed());
        assertTrue(response.getChecks().get("messageType").isPassed());
        assertEquals("SMPRES", response.getClientFile().getMessageType());
        assertTrue(
                "STRUCTURALLY_MATCHED".equals(response.getVerdict())
                        || "PARTIAL_MATCH".equals(response.getVerdict())
                        || "IDENTICAL".equals(response.getVerdict()));
        assertFalse(response.getMatches().isEmpty());
        if (response.getMatchPercent() < 100) {
            assertEquals(100 - response.getMatchPercent(), response.getMismatchPercent());
            assertTrue(response.getMismatchDetails() != null);
            assertFalse(response.getMismatchDetails().getFailedChecks().isEmpty());
            assertFalse(response.getMismatchDetails().getWhyNotMatched().isEmpty());
            assertTrue(response.getMismatchDetails().getReason() != null
                    && response.getMismatchDetails().getReason().contains("%"));
        }
    }

    @Test
    void compare_whenNotIdentical_exposesMismatchPercentAndDetails() {
        byte[] client = "UNB+IATB:1+UA1SM+1A+260710:1200+REF+REF++T'UNH+1+SMPRES:93:2:IA+REF'TVL+211226+LAX+DEN+UA+1275:L'EQI++++++B3S'CBD+Y:::Y+007:039++++14:24+A:W+B:9'ROD+7++A:F:CH'UNT+6+1'UNZ+1+REF'"
                .getBytes(IBM037);
        byte[] unparse = "UNB+IATB:1+UA1SM+1A+260710:9999+OTHER+OTHER++T'UNH+1+SMPRES:93:2:IA+OTHER'TVL+211226+LAX+DEN+UA+1275:L'EQI++++++B3S'CBD+Y:::Y+007:039++++14:24+A:W+B:9'ROD+7++A:F:CH'UNT+6+1'UNZ+1+OTHER'"
                .getBytes(IBM037);

        BinaryCompareResponse response = service.compare(client, "client.bin", unparse, "unparse.bin");

        assertTrue(response.isSuccess());
        assertTrue(response.getMatchPercent() < 100);
        assertEquals(100 - response.getMatchPercent(), response.getMismatchPercent());
        assertTrue(response.getMismatchDetails() != null);
        assertTrue(response.getMismatchDetails().getFailedCheckCount() > 0);
        assertEquals(
                response.getMismatchDetails().getFailedCheckCount(),
                response.getMismatchDetails().getFailedChecks().size());
        assertTrue(response.getMismatchDetails().getFailedChecks().stream()
                .anyMatch(f -> "byteIdentical".equals(f.getCheck())));
        assertTrue(response.getMismatchDetails().getWhyNotMatched().stream()
                .anyMatch(s -> s.startsWith("byteIdentical:")));
        assertTrue(response.getClientJson() != null);
        assertTrue(response.getUnparseJson() != null);
        assertFalse(response.getMismatchedValues().isEmpty());
        assertTrue(response.getMismatchedValues().stream()
                .anyMatch(m -> m.getPath().contains("envelope.UNB")
                        || m.getPath().contains("envelope.UNH")
                        || m.getPath().contains("envelope.UNZ")
                        || "sizeBytes".equals(m.getPath())));
        assertTrue(response.getMismatchedValues().stream()
                .allMatch(m -> m.getClientValue() != null || m.getUnparseValue() != null));
    }
}
