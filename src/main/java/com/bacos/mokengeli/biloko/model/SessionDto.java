package com.bacos.mokengeli.biloko.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    private String jti;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
}