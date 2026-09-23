package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsReportResponse;

public interface AwsReportPdfService {
    byte[] generate(AwsReportResponse report);
}