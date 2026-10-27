package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsReportResponse;

import java.time.LocalDate;

public interface AwsReportService {
    AwsReportResponse getReport(String connectionToken, String month);

    AwsReportResponse getReport(String connectionToken, ReportParameters parameters);

    record ReportParameters(String month, LocalDate startDate, LocalDate endDate,
                            String granularity, String service, String region, String groupBy) {
        public static ReportParameters forMonth(String month) {
            return new ReportParameters(month, null, null, null, null, null, null);
        }
    }
}