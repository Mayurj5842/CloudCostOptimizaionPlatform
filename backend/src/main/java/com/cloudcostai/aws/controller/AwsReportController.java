package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsReportResponse;
import com.cloudcostai.aws.service.AwsReportService;
import com.cloudcostai.aws.service.AwsReportPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsReportController {
    private final AwsReportService reportService;
    private final AwsReportPdfService reportPdfService;

    public AwsReportController(AwsReportService reportService, AwsReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<AwsReportResponse>> report(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String region,
            @RequestParam(required = false, defaultValue = "Service") String groupBy) {
        AwsReportService.ReportParameters parameters = parseParameters(month, startDate, endDate, granularity,
                service, region, groupBy);
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS report fetched successfully",
                reportService.getReport(connectionToken, parameters)));
    }

    @GetMapping(value = "/reports/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reportPdf(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String region,
            @RequestParam(required = false, defaultValue = "Service") String groupBy) {
        AwsReportService.ReportParameters parameters = parseParameters(month, startDate, endDate, granularity,
                service, region, groupBy);
        AwsReportResponse report = reportService.getReport(connectionToken, parameters);
        if ("NO_DATA".equals(report.status())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        byte[] pdf = reportPdfService.generate(report);
        String filename = "CloudCostAI-AWS-Report-" + (report.billingMonth() != null ? report.billingMonth() : "custom") + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private AwsReportService.ReportParameters parseParameters(String month, String startDate, String endDate,
                                                             String granularity, String service, String region,
                                                             String groupBy) {
        java.time.LocalDate start = startDate == null || startDate.isBlank() ? null : java.time.LocalDate.parse(startDate);
        java.time.LocalDate end = endDate == null || endDate.isBlank() ? null : java.time.LocalDate.parse(endDate);
        return new AwsReportService.ReportParameters(month, start, end, granularity, service, region, groupBy);
    }
}