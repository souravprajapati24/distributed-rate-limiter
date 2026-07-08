package com.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TestingController {
    @GetMapping("/test")
    public ResponseEntity<String> probe() {
        return ResponseEntity.ok("Rate limiter is working");
    }
}
