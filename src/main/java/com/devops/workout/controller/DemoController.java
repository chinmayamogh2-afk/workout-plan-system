package com.devops.workout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the public interactive demo page.
 *
 * The demo page is intentionally public (no authentication required) so that
 * evaluators, assessors, and prospective users can explore the system without
 * needing to configure credentials.
 *
 * All data displayed in the demo is local JavaScript state and is never
 * persisted to the production database.
 */
@Controller
@RequestMapping("/demo")
public class DemoController {

    /**
     * Serves the demo landing page.
     * No model attributes are required – all demo data is managed client-side
     * by main.js using isolated in-memory state.
     */
    @GetMapping
    public String demo() {
        return "demo";
    }
}
