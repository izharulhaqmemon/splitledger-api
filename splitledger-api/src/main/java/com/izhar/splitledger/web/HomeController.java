package com.izhar.splitledger.web;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@Tag(name = "Health")
public class HomeController {

    /** Opening the bare URL in a browser lands on the interactive API docs. */
    @Hidden
    @GetMapping("/")
    public String home() {
        return "redirect:/swagger-ui.html";
    }

    @GetMapping("/health")
    @ResponseBody
    @Operation(summary = "Liveness check")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "splitledger-api");
    }
}
