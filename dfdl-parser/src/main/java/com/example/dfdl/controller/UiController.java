package com.example.dfdl.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves browser UI pages (separate from JSON API endpoints).
 */
@Controller
public class UiController {

    /**
     * Compare UI — upload client + unparse binaries and view formatted JSON results.
     * API remains {@code POST /compare}.
     */
    @GetMapping({"/ui/compare", "/ui/compare/"})
    public String compareUi() {
        return "forward:/ui/compare.html";
    }

    @GetMapping({"/ui", "/ui/"})
    public String uiHome() {
        return "redirect:/ui/compare";
    }
}
