package com.cloudcostai.aws.dto.response;

public record AwsConnectionResponse(

        String accountId,

        String arn,

        String userId,

        String region,

        String connectionStatus,

        String accountAlias,

        String provider,

        String connectionToken

) {
}