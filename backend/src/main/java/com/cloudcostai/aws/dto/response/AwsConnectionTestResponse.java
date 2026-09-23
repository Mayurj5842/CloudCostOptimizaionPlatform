package com.cloudcostai.aws.dto.response;

public record AwsConnectionTestResponse(String accountId, String region, String status, String message) {
}