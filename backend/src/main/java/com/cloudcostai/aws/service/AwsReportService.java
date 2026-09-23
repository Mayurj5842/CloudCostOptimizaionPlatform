package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsReportResponse;

public interface AwsReportService {
    AwsReportResponse getReport(String connectionToken, String month);
}