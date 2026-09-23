package com.cloudcostai.aws.repository;

import com.cloudcostai.aws.model.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AwsAccountRepository extends JpaRepository<AwsAccount, UUID> {
    Optional<AwsAccount> findByUserIdAndProvider(String userId, String provider);

    Optional<AwsAccount> findByAccountIdAndProviderAndConnectionStatus(String accountId, String provider, String connectionStatus);

    Optional<AwsAccount> findFirstByProviderAndConnectionStatusOrderByUpdatedAtDesc(String provider, String connectionStatus);
}