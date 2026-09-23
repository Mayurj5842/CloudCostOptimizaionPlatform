package com.cloudcostai.auth.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AppSessionTest {
    @Test
    void newSessionPopulatesCreatedAtAndPreservesExpiry() {
        Instant expiresAt = Instant.parse("2026-10-23T00:00:00Z");

        AppSession session = new AppSession("token", null, expiresAt);

        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getExpiresAt()).isEqualTo(expiresAt);
    }
}