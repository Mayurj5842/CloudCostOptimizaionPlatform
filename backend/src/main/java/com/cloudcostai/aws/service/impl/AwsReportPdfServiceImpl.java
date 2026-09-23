package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostBreakdown;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostDriver;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsInsightResponse.Insight;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.dto.response.AwsReportResponse;
import com.cloudcostai.aws.service.AwsReportPdfService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class AwsReportPdfServiceImpl implements AwsReportPdfService {
    private static final Color NAVY = new Color(15, 40, 70);
    private static final Color BLUE = new Color(28, 100, 180);

    @Override
    public byte[] generate(AwsReportResponse report) {
        if ("NO_DATA".equals(report.status())) {
            throw new IllegalStateException("No valid CUR billing data was found for the selected billing period.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 48, 44);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new FooterEvent());
            document.open();
            addCover(document, report);
            addSummary(document, report);
            addServiceBreakdown(document, report);
            addTrend(document, report);
            addDrivers(document, report);
            addResources(document, report);
            addInsights(document, report);
            addRecommendations(document, report);
            addBudgetsAndAlerts(document, report);
            addBillingData(document, report);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate PDF report", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to close PDF report", exception);
        }
    }

    private void addCover(Document document, AwsReportResponse report) throws DocumentException {
        Paragraph brand = new Paragraph("CloudCostAI", font(24, Font.BOLD, NAVY));
        brand.setSpacingAfter(8);
        document.add(brand);
        document.add(new Paragraph("AWS FinOps Monthly Report", font(18, Font.BOLD, BLUE)));
        document.add(new Paragraph("Account: " + report.accountId(), font(10, Font.NORMAL, Color.DARK_GRAY)));
        document.add(new Paragraph("Billing Month: " + report.billingMonth(), font(10, Font.NORMAL, Color.DARK_GRAY)));
        document.add(new Paragraph("Billing Period: " + report.billingPeriodStart() + " - " + report.billingPeriodEnd(), font(10, Font.NORMAL, Color.DARK_GRAY)));
        document.add(new Paragraph("Generated At: " + report.generatedAt(), font(10, Font.NORMAL, Color.DARK_GRAY)));
        document.add(Chunk.NEWLINE);
        addHeading(document, "EXECUTIVE SUMMARY");
    }

    private void addSummary(Document document, AwsReportResponse report) throws DocumentException {
        AwsReportResponse.Summary summary = report.summary();
        PdfPTable table = table(4);
        summaryCell(table, "Total AWS Cost", money(summary.totalCost(), report.currency()));
        summaryCell(table, "Previous Month", money(summary.previousMonthCost(), report.currency()));
        summaryCell(table, "Change", money(summary.changeAmount(), report.currency()));
        summaryCell(table, "Change Percentage", percent(summary.changePercentage()));
        document.add(table);
    }

    private void addServiceBreakdown(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "SERVICE COST BREAKDOWN");
        PdfPTable table = table(3);
        header(table, "Service", "Cost", "Percentage");
        for (CostBreakdown item : report.serviceBreakdown()) {
            cell(table, item.name(), false);
            cell(table, money(item.cost(), report.currency()), false);
            cell(table, percent(item.cost().multiply(BigDecimal.valueOf(100)).divide(report.summary().totalCost(), 2, RoundingMode.HALF_UP)), false);
        }
        document.add(table);
    }

    private void addTrend(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "COST TREND");
        PdfPTable table = table(2);
        header(table, "Date", "Cost");
        for (CostPoint point : report.costTrend()) {
            cell(table, point.date(), false);
            cell(table, money(point.cost(), report.currency()), false);
        }
        document.add(table);
    }

    private void addDrivers(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "TOP COST DRIVERS");
        PdfPTable table = table(3);
        header(table, "Service", "Cost", "Share");
        for (CostDriver driver : report.topCostDrivers()) {
            cell(table, driver.service(), false);
            cell(table, money(driver.cost(), report.currency()), false);
            cell(table, percent(driver.percentage()), false);
        }
        document.add(table);
    }

    private void addResources(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "AWS RESOURCE SUMMARY");
        if (report.resourceSummary() == null) {
            document.add(new Paragraph("Resource data unavailable", font(10, Font.ITALIC, Color.DARK_GRAY)));
            return;
        }
        var summary = report.resourceSummary();
        PdfPTable table = table(2);
        header(table, "Resource", "Summary");
        row(table, "EC2 Instances", summary.ec2Instances() + " total, " + summary.runningEc2Instances() + " running, " + summary.stoppedEc2Instances() + " stopped");
        row(table, "EBS Volumes", summary.ebsVolumes() + " total, " + summary.unattachedEbsVolumes() + " unattached");
        row(table, "S3 Buckets", String.valueOf(summary.s3Buckets()));
        row(table, "RDS Databases", String.valueOf(summary.rdsDatabases()));
        row(table, "Lambda Functions", String.valueOf(summary.lambdaFunctions()));
        row(table, "VPCs", String.valueOf(summary.vpcs()));
        row(table, "Security Groups", String.valueOf(summary.securityGroups()));
        document.add(table);
    }

    private void addInsights(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "AI INSIGHTS");
        if (report.insights().isEmpty()) {
            document.add(new Paragraph("No insights for this billing period.", font(10, Font.ITALIC, Color.DARK_GRAY)));
            return;
        }
        for (Insight insight : report.insights()) {
            document.add(new Paragraph("[" + insight.severity() + "] " + insight.category(), font(11, Font.BOLD, NAVY)));
            document.add(new Paragraph(insight.service() + " / " + insight.region(), font(9, Font.NORMAL, BLUE)));
            document.add(new Paragraph(insight.reason(), font(9, Font.NORMAL, Color.DARK_GRAY)));
        }
    }

    private void addRecommendations(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "RECOMMENDATIONS");
        if (report.recommendations().isEmpty()) {
            document.add(new Paragraph("No recommendations for this billing period.", font(10, Font.ITALIC, Color.DARK_GRAY)));
            return;
        }
        for (Recommendation recommendation : report.recommendations()) {
            document.add(new Paragraph("[" + recommendation.priority() + "] " + recommendation.problem(), font(11, Font.BOLD, NAVY)));
            rowParagraph(document, "Service", recommendation.service());
            rowParagraph(document, "Resource", recommendation.resource());
            rowParagraph(document, "Current Cost", money(recommendation.currentCost(), report.currency()));
            rowParagraph(document, "Estimated Monthly Saving", money(recommendation.estimatedMonthlySaving(), report.currency()));
            rowParagraph(document, "Evidence", recommendation.reason());
            rowParagraph(document, "Recommended Action", recommendation.recommendedAction());
        }
    }

    private void addBillingData(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "BILLING DATA");
        rowParagraph(document, "Data Source", "AWS CUR 2.0 -> S3 -> PostgreSQL");
        rowParagraph(document, "Report Source", report.dataSource());
        rowParagraph(document, "Billing Period", report.billingPeriodStart() + " - " + report.billingPeriodEnd());
        rowParagraph(document, "Last Updated", report.lastUpdated() == null ? "Unavailable" : report.lastUpdated().toString());
        rowParagraph(document, "Report Generated", report.generatedAt().toString());
        rowParagraph(document, "Report Status", report.status());
        if (report.message() != null) rowParagraph(document, "Note", report.message());
        addHeading(document, "POTENTIAL MONTHLY SAVINGS");
        document.add(new Paragraph(money(report.savingsSummary().potentialMonthlySavings(), report.currency()), font(14, Font.BOLD, BLUE)));
    }

    private void addBudgetsAndAlerts(Document document, AwsReportResponse report) throws DocumentException {
        addHeading(document, "BUDGET USAGE");
        if (report.budgets().isEmpty()) {
            document.add(new Paragraph("No budget configured.", font(10, Font.ITALIC, Color.DARK_GRAY)));
        } else {
            PdfPTable table = table(5);
            header(table, "Budget", "Amount", "Actual Spend", "Usage", "Status");
            report.budgets().forEach(budget -> {
                cell(table, budget.name(), false);
                cell(table, money(budget.amount(), budget.currency()), false);
                cell(table, money(budget.actualSpend(), budget.currency()), false);
                cell(table, percent(budget.usagePercentage()), false);
                cell(table, budget.status(), false);
            });
            document.add(table);
        }
        addHeading(document, "ALERTS");
        if (report.alerts().isEmpty()) {
            document.add(new Paragraph("No alerts triggered for this billing period.", font(10, Font.ITALIC, Color.DARK_GRAY)));
        } else {
            report.alerts().forEach(alert -> rowParagraph(document, alert.type() + " " + alert.threshold() + "%", alert.message()));
        }
    }

    private void addHeading(Document document, String text) throws DocumentException { Paragraph heading = new Paragraph(text, font(12, Font.BOLD, BLUE)); heading.setSpacingBefore(16); heading.setSpacingAfter(7); document.add(heading); }
    private void rowParagraph(Document document, String label, String value) throws DocumentException { document.add(new Paragraph(label + ": " + value, font(9, Font.NORMAL, Color.DARK_GRAY))); }
    private void row(PdfPTable table, String label, String value) { cell(table, label, false); cell(table, value, false); }
    private PdfPTable table(int columns) { PdfPTable table = new PdfPTable(columns); table.setWidthPercentage(100); table.setSpacingAfter(8); return table; }
    private void header(PdfPTable table, String... values) { for (String value : values) cell(table, value, true); }
    private void cell(PdfPTable table, String value, boolean heading) { PdfPCell cell = new PdfPCell(new Phrase(value == null ? "Unavailable" : value, font(9, heading ? Font.BOLD : Font.NORMAL, heading ? Color.WHITE : Color.DARK_GRAY))); if (heading) cell.setBackgroundColor(BLUE); cell.setPadding(6); table.addCell(cell); }
    private void summaryCell(PdfPTable table, String label, String value) { PdfPCell cell = new PdfPCell(); cell.setPadding(8); cell.addElement(new Paragraph(label, font(8, Font.BOLD, BLUE))); cell.addElement(new Paragraph(value, font(13, Font.BOLD, NAVY))); table.addCell(cell); }
    private Font font(float size, int style, Color color) { return new Font(Font.HELVETICA, size, style, color); }
    private String money(BigDecimal value, String currency) { return value == null ? "Unavailable" : NumberFormat.getCurrencyInstance(Locale.US).format(value) + " " + currency; }
    private String percent(BigDecimal value) { return value == null ? "Unavailable" : value.setScale(2, RoundingMode.HALF_UP) + "%"; }

    private static class FooterEvent extends PdfPageEventHelper {
        @Override public void onEndPage(PdfWriter writer, Document document) {
            String footer = "CloudCostAI  |  Page " + writer.getPageNumber();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(footer, new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY)),
                    (document.left() + document.right()) / 2f, 24f, 0f);
        }
    }
}