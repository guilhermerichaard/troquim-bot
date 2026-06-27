package com.troquim_bot;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lead")
public class LeadController {

    @PostMapping
    public String receberLead() {
        return "Lead recebido!";
    }
}