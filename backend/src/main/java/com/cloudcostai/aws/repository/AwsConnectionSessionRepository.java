package com.cloudcostai.aws.repository;

import com.cloudcostai.aws.model.AwsConnectionSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AwsConnectionSessionRepository extends JpaRepository<AwsConnectionSession, String> {
    Optional<AwsConnectionSession> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}
