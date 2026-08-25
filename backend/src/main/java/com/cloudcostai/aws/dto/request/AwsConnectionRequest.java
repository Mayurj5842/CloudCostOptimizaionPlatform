package com.cloudcostai.aws.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AwsConnectionRequest(

        @NotBlank(message = "AWS account alias is required")
        String accountAlias,

        @NotBlank(message = "AWS access key is required")
        String accessKey,

        @NotBlank(message = "AWS secret key is required")
        String secretKey,

        @NotBlank(message = "AWS region is required")
        String region

) {
}