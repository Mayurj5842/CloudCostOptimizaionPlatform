package com.cloudcostai.aws.service;

import com.cloudcostai.aws.service.impl.AwsConnectionServiceImpl.AwsIdentity;

public interface AwsConnectionService {

    AwsIdentity verifyConnection(
            String accessKey,
            String secretKey,
            String region
    );
}