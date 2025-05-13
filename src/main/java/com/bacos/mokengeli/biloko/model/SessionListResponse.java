package com.bacos.mokengeli.biloko.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTOs pour la désérialisation de la réponse "liste"
 */
@Data
@NoArgsConstructor
public class SessionListResponse {
    private String employeeNumber;
    private String appType;
    private Integer maxSessions;
    private List<SessionDto> sessions;

    public List<String> extractJtis() {
        return sessions == null ? List.of() : sessions.stream()
                .map(SessionDto::getJti)
                .collect(Collectors.toList());
    }
}
