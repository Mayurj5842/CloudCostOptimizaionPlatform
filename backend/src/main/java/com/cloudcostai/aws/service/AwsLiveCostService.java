package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsLiveCostResponse;

public interface AwsLiveCostService {
    AwsLiveCostResponse fetch(String connectionToken, String month);
}
