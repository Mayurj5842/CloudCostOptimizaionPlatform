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
            @RequestParam String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS report fetched successfully",
                reportService.getReport(connectionToken, month)));
    }

    @GetMapping(value = "/reports/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reportPdf(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam String month) {
        AwsReportResponse report = reportService.getReport(connectionToken, month);
        if ("NO_DATA".equals(report.status())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        byte[] pdf = reportPdfService.generate(report);
        String filename = "CloudCostAI-AWS-Report-" + month + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}