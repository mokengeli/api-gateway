package com.bacos.mokengeli.biloko.service;

import com.bacos.mokengeli.biloko.model.SessionListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "${authentication.service-id}")
public interface AuthInternalClient {

    @GetMapping("/api/auth/internal/jti")
    SessionListResponse list(@RequestHeader("Cookie") String cookieHeader,
                             @RequestParam("employeeNumber") String employeeNumber,
                             @RequestParam("appType") String appType);
}
