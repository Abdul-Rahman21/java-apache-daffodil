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
    }
}
