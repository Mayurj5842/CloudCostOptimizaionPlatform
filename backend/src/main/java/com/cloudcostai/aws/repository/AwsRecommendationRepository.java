package com.cloudcostai.aws.repository;

import com.cloudcostai.aws.model.AwsRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwsRecommendationRepository extends JpaRepository<AwsRecommendation, UUID> {
    List<AwsRecommendation> findByAwsAccountIdOrderByCreatedAtDesc(String awsAccountId);
    Optional<AwsRecommendation> findByAwsAccountIdAndRecommendationKey(String awsAccountId, String recommendationKey);
}