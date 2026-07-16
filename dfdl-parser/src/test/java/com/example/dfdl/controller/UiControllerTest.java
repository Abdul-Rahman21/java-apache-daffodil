package com.example.dfdl.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UiController.class)
class UiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void compareUi_forwardsToStaticPage() throws Exception {
        mockMvc.perform(get("/ui/compare"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ui/compare.html"));
    }

    @Test
    void uiHome_redirectsToCompare() throws Exception {
        mockMvc.perform(get("/ui"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/compare"));
    }
}
