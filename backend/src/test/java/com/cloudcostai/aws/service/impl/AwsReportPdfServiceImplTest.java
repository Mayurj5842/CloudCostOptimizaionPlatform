package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsReportResponse;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostBreakdown;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostDriver;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsResourceResponse.AwsResourceSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsReportPdfServiceImplTest {
    private final AwsReportPdfServiceImpl service = new AwsReportPdfServiceImpl();

    @Test
    void generatesPdfFromExistingReportData() {
        AwsReportResponse report = report("SUCCESS");

        byte[] pdf = service.generate(report);

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void refusesNoDataReport() {
        assertThatThrownBy(() -> service.generate(report("NO_DATA")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No billing data");
    }

    private AwsReportResponse report(String status) {
        return new AwsReportResponse("123456789012", status, "CUR_S3_POSTGRESQL", "2026-08", "2026-08-01", "2026-08-31",
                Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"), "USD",
                new AwsReportResponse.Summary(new BigDecimal("30.00"), new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("50.00")),
                List.of(new CostBreakdown("Amazon EC2", new BigDecimal("30.00"))),
                List.of(new CostPoint("2026-08-31", new BigDecimal("30.00"))),
                List.of(new CostDriver("Amazon EC2", new BigDecimal("30.00"), new BigDecimal("100.00"))),
                new AwsResourceSummary(1, 1, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0), "AVAILABLE",
                List.of(), List.of(), new AwsReportResponse.SavingsSummary(null, false), null);
    }
}
