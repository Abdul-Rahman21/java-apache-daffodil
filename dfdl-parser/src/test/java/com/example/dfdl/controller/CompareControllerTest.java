package com.example.dfdl.controller;

import com.example.dfdl.dto.BinaryCompareResponse;
import com.example.dfdl.exception.GlobalExceptionHandler;
import com.example.dfdl.service.BinaryCompareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompareController.class)
@Import(GlobalExceptionHandler.class)
class CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BinaryCompareService binaryCompareService;

    @Test
    void compare_returnsStructuredResult() throws Exception {
        BinaryCompareResponse response = new BinaryCompareResponse();
        response.setSuccess(true);
        response.setVerdict("STRUCTURALLY_MATCHED");
        response.setMatchPercent(90);
        response.setMismatchPercent(10);
        response.setSummary("ACE layout matches");

        when(binaryCompareService.compare(any(), anyString(), any(), anyString())).thenReturn(response);

        MockMultipartFile client = new MockMultipartFile(
                "clientFile", "Response_SMPRES_4.bin", "application/octet-stream", new byte[] {1, 2});
        MockMultipartFile unparse = new MockMultipartFile(
                "unparseFile", "SMPRES.bin", "application/octet-stream", new byte[] {3, 4});

        mockMvc.perform(multipart("/compare").file(client).file(unparse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.verdict").value("STRUCTURALLY_MATCHED"))
                .andExpect(jsonPath("$.matchPercent").value(90))
                .andExpect(jsonPath("$.mismatchPercent").value(10));
    }
}
