package com.cloudcostai.aws.service;

import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.impl.AwsConnectionServiceImpl.AwsIdentity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class AwsConnectionSessionService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private final ConcurrentHashMap<String, ConnectionSession> sessions = new ConcurrentHashMap<>();

    public String createSession(String accessKey, String secretKey, String region, AwsIdentity identity) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new ConnectionSession(accessKey, secretKey, region, identity.accountId(), Instant.now().plus(SESSION_TTL)));
        return token;
    }

    public ConnectionSession getSession(String token) {
        if (token == null || token.isBlank()) {
            throw new AwsConnectionException("AWS connection is not available. Please connect an AWS account first.");
        }
        ConnectionSession session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new AwsConnectionException("AWS connection has expired. Please connect the account again.");
        }
        return session;
    }

    public Optional<String> findTokenByAccountId(String accountId) {
        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().accountId().equals(accountId))
                .filter(entry -> entry.getValue().expiresAt().isAfter(Instant.now()))
                .map(java.util.Map.Entry::getKey)
                .findFirst();
    }

    public void invalidateByAccountId(String accountId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
    }

    public record ConnectionSession(String accessKey, String secretKey, String region, String accountId, Instant expiresAt) {
    }
}