package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsRecentActivityResponse;

public interface AwsRecentActivityService {
    AwsRecentActivityResponse getRecentActivity(String connectionToken, int limit);
}
