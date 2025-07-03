package com.bacos.mokengeli.biloko.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {
    @GetMapping("mobile-test")
    public ResponseEntity<?> mobileTest() {
        long l = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        log.info("mobileTest has been call at {} ", now);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "date", now
        ));
    }
}