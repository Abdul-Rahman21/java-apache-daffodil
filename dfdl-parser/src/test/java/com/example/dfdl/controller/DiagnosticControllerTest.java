package com.example.dfdl.controller;

import com.example.dfdl.dto.DiagnosticResponse;
import com.example.dfdl.service.DiagnosticService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DiagnosticController.class)
class DiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosticService diagnosticService;

    @Test
    void diagnose_withoutFile_returnsCompileDiagnostics() throws Exception {
        DiagnosticResponse response = new DiagnosticResponse();
        response.setCompileSuccess(true);
        response.setCompileDiagnostics(List.of("INFO: compiled"));
        response.setSchemaPath("/app/schema/CYO_SMPREQ.xsd");
        response.setElapsedMillis(12L);
        response.setNotes("compatibility probe");

        when(diagnosticService.diagnose(isNull())).thenReturn(response);

        mockMvc.perform(post("/diagnose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileSuccess").value(true))
                .andExpect(jsonPath("$.schemaPath").value("/app/schema/CYO_SMPREQ.xsd"))
                .andExpect(jsonPath("$.elapsedMillis").value(12));
    }

    @Test
    void diagnose_withFile_passesBytesToService() throws Exception {
        DiagnosticResponse response = new DiagnosticResponse();
        response.setCompileSuccess(true);
        response.setParseSuccess(true);
        response.setCompileDiagnostics(List.of());
        response.setParseDiagnostics(List.of());
        response.setSchemaPath("/app/schema/CYO_SMPREQ.xsd");
        response.setElapsedMillis(25L);

        when(diagnosticService.diagnose(any(byte[].class))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[] {0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/diagnose").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileSuccess").value(true))
                .andExpect(jsonPath("$.parseSuccess").value(true));
    }
}
