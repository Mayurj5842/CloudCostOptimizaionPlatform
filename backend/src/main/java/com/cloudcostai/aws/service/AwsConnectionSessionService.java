package com.cloudcostai.aws.service;

import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.model.AwsConnectionSession;
import com.cloudcostai.aws.repository.AwsConnectionSessionRepository;
import com.cloudcostai.aws.service.impl.AwsConnectionServiceImpl.AwsIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class AwsConnectionSessionService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final String MISSING_MESSAGE = "AWS connection is not available. Please connect an AWS account first.";
    private static final String EXPIRED_MESSAGE = "AWS connection has expired. Please connect the account again.";

    private final ConcurrentHashMap<String, ConnectionSession> sessions = new ConcurrentHashMap<>();
    private final AwsConnectionSessionRepository sessionRepository;

    public AwsConnectionSessionService(AwsConnectionSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public String createSession(String accessKey, String secretKey, String region, AwsIdentity identity) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        sessionRepository.deleteByAccountId(identity.accountId());
        sessionRepository.flush();
        sessionRepository.save(new AwsConnectionSession(
                token, identity.accountId(), accessKey, secretKey, region, expiresAt));
        sessions.put(token, new ConnectionSession(accessKey, secretKey, region, identity.accountId(), expiresAt));
        return token;
    }

    public ConnectionSession getSession(String token) {
        if (token == null || token.isBlank()) {
            throw new AwsConnectionException(MISSING_MESSAGE);
        }
        ConnectionSession cached = sessions.get(token);
        if (cached != null) {
            if (cached.expiresAt().isBefore(Instant.now())) {
                sessions.remove(token);
                throw new AwsConnectionException(EXPIRED_MESSAGE);
            }
            return cached;
        }
        return sessionRepository.findById(token)
                .map(entity -> {
                    if (entity.getExpiresAt().isBefore(Instant.now())) {
                        throw new AwsConnectionException(EXPIRED_MESSAGE);
                    }
                    ConnectionSession session = new ConnectionSession(entity.getAccessKey(), entity.getSecretKey(),
                            entity.getRegion(), entity.getAccountId(), entity.getExpiresAt());
                    sessions.put(token, session);
                    return session;
                })
                .orElseThrow(() -> new AwsConnectionException(EXPIRED_MESSAGE));
    }

    public Optional<String> findTokenByAccountId(String accountId) {
        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().accountId().equals(accountId))
                .filter(entry -> entry.getValue().expiresAt().isAfter(Instant.now()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .or(() -> sessionRepository.findByAccountId(accountId)
                        .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                        .map(AwsConnectionSession::getToken));
    }

    @Transactional
    public void invalidateByAccountId(String accountId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
        sessionRepository.deleteByAccountId(accountId);
    }

    public record ConnectionSession(String accessKey, String secretKey, String region, String accountId, Instant expiresAt) {
    }
}
