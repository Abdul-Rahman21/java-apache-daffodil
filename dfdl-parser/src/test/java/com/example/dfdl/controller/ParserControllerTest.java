package com.example.dfdl.controller;

import com.example.dfdl.dto.ParseResponse;
import com.example.dfdl.exception.DfdlParseException;
import com.example.dfdl.exception.GlobalExceptionHandler;
import com.example.dfdl.service.DaffodilParserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ParserController.class)
@Import(GlobalExceptionHandler.class)
class ParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DaffodilParserService parserService;

    @Test
    void health_returnsUpWhenSchemaCompiled() throws Exception {
        when(parserService.isSchemaCompiled()).thenReturn(true);
        when(parserService.getSchemaFileName()).thenReturn("CYO_SMPREQ.xsd");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schemaCompiled").value(true))
                .andExpect(jsonPath("$.schema").value("CYO_SMPREQ.xsd"));
    }

    @Test
    void health_returnsDownWhenSchemaNotCompiled() throws Exception {
        when(parserService.isSchemaCompiled()).thenReturn(false);
        when(parserService.getSchemaFileName()).thenReturn("CYO_SMPREQ.xsd");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.schemaCompiled").value(false));
    }

    @Test
    void parse_validUpload_returnsSuccessPayload() throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("msgType", "REQ1");
        ParseResponse response = ParseResponse.ok("<CYO_SMPREQ><msgType>REQ1</msgType></CYO_SMPREQ>", json);

        when(parserService.parse(any(byte[].class))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[] {0x00, 0x01, 0x02});

        mockMvc.perform(multipart("/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.xml").exists())
                .andExpect(jsonPath("$.json.msgType").value("REQ1"));
    }

    @Test
    void parse_failure_returnsHttp400() throws Exception {
        when(parserService.parse(any(byte[].class)))
                .thenThrow(new DfdlParseException("DFDL parse failed. Diagnostics: ERROR: Truncated data"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[] {0x00});

        mockMvc.perform(multipart("/parse").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("parse failed")));
    }

    @Test
    void parseSample_delegatesToService() throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("payload", "HELLO");
        when(parserService.parseSampleFile(eq("sample_smpreq.bin")))
                .thenReturn(ParseResponse.ok("<root/>", json));

        mockMvc.perform(post("/parse/sample/sample_smpreq.bin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.json.payload").value("HELLO"));
    }
}
