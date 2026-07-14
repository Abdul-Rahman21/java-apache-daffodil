package com.example.dfdl.controller;

import com.example.dfdl.exception.GlobalExceptionHandler;
import com.example.dfdl.service.SeatMapUnparseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UnparseController.class)
@Import(GlobalExceptionHandler.class)
class UnparseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatMapUnparseService seatMapUnparseService;

    @Test
    void unparse_returnsBinaryAttachment() throws Exception {
        byte[] binary = new byte[] {0x01, 0x02, 0x03};
        when(seatMapUnparseService.unparseSeatMapResponse(any(JsonNode.class))).thenReturn(binary);

        String body = """
                {"flightInfo":{"marketingFlightNumber":1275},"cabins":[]}
                """;

        mockMvc.perform(post("/unparse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"SMPRES.bin\""))
                .andExpect(header().string("X-DFDL-Binary-Encoding", "IBM037"))
                .andExpect(header().string("X-DFDL-Binary-Encoding-Name", "EBCDIC"));
    }
}
