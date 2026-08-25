package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsResourceResponse;

public interface AwsResourceService {
    AwsResourceResponse discoverResources(String connectionToken);
}
