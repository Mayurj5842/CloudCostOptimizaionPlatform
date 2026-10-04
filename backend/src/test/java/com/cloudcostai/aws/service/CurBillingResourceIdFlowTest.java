package com.cloudcostai.aws.service;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.controller.AwsBillingController;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsBillingSummary;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsBillingSummaryRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.impl.AwsRecommendationServiceImpl;
import com.cloudcostai.aws.service.impl.AwsBillingSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurBillingResourceIdFlowTest {
    private static final String ACCOUNT_ID = "123456789012";

    @Mock private CurS3Service s3Service;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsBillingSummaryRepository summaryRepository;
    @Mock private AwsBillingSyncProperties syncProperties;
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsRecommendationRepository recommendationRepository;
    @Mock private AwsResourceService resourceService;
        @Mock private CurBillingImportService curBillingImportService;

    @Test
        void csvEc2AndEbsResourceIdsPersistResolveAndGenerateFindings() throws Exception {
        String csv = "line_item_product_code,line_item_resource_id,line_item_usage_account_id,product_region_code,"
                + "line_item_usage_start_date,line_item_usage_end_date,line_item_unblended_cost,"
                                + "line_item_usage_amount,line_item_currency_code,line_item_line_item_type,product_product_name\n"
                + "AmazonEC2,i-test123," + ACCOUNT_ID + ",ap-south-1,"
                                + "2026-09-10T00:00:00Z,2026-09-10T01:00:00Z,0.03,1,USD,Usage,Amazon Elastic Compute Cloud\n"
                                + "AmazonEC2,vol-test123," + ACCOUNT_ID + ",ap-south-1,"
                                                + "2026-09-10T00:00:00Z,2026-09-10T01:00:00Z,0.16,40,USD,Usage,Amazon Elastic Block Store\n";
        CurS3Service parser = new CurS3Service(org.mockito.Mockito.mock(AwsClientFactory.class), syncProperties);
        Method parse = CurS3Service.class.getDeclaredMethod("parse", byte[].class, String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CurS3Service.Row> parsedRows = (List<CurS3Service.Row>) parse.invoke(parser,
                csv.getBytes(StandardCharsets.UTF_8), "part-000.csv");

        assertThat(parsedRows).hasSize(2);
        assertThat(parsedRows).extracting(row -> row.values().get("line_item_resource_id"))
                .containsExactly("i-test123", "vol-test123");
        when(syncProperties.isEnabled()).thenReturn(true);
        when(s3Service.readLatest("access", "secret"))
                .thenReturn(new CurS3Service.Export("part-000.csv", Instant.parse("2026-09-11T00:00:00Z"), parsedRows));
        when(billingRepository.findByRecordKey(anyString())).thenReturn(Optional.empty());
        when(summaryRepository.findByAwsAccountIdAndBillingPeriod(eq(ACCOUNT_ID), anyString()))
                .thenReturn(Optional.empty());

        new CurBillingImportService(s3Service, billingRepository, summaryRepository, syncProperties)
                .importLatest("access", "secret", ACCOUNT_ID);

        ArgumentCaptor<AwsBilling> savedBilling = ArgumentCaptor.forClass(AwsBilling.class);
        verify(billingRepository, org.mockito.Mockito.times(2)).save(savedBilling.capture());
        List<AwsBilling> persistedBilling = savedBilling.getAllValues();
        assertThat(persistedBilling).extracting(AwsBilling::getResourceId)
                .containsExactlyInAnyOrder("i-test123", "vol-test123");
        assertThat(persistedBilling).allSatisfy(row -> {
            assertThat(row.getAwsAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(row.getRegion()).isEqualTo("ap-south-1");
        });
        assertThat(persistedBilling).filteredOn(row -> "vol-test123".equals(row.getResourceId()))
                .singleElement().satisfies(row -> assertThat(row.getUsageAmount()).isEqualByComparingTo("40"));

        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(ACCOUNT_ID, "CUR_2_0"))
                .thenReturn(persistedBilling);
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(ACCOUNT_ID, "COST_EXPLORER"))
                .thenReturn(List.of());
        AwsBillingSourceResolver resolver = new AwsBillingSourceResolver(billingRepository);
        assertThat(resolver.resolve(ACCOUNT_ID, YearMonth.parse("2026-09")).rows())
                .extracting(AwsBilling::getResourceId).containsExactlyInAnyOrder("i-test123", "vol-test123");

        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                "access", "secret", "ap-south-1", ACCOUNT_ID, Instant.now().plusSeconds(600)));
        when(resourceService.discoverResources("token")).thenReturn(stoppedInstanceAndUnattachedVolume());
        when(recommendationRepository.findByAwsAccountIdAndRecommendationKey(ACCOUNT_ID, "stopped-ec2-i-test123"))
                .thenReturn(Optional.empty());
        when(recommendationRepository.findByAwsAccountIdAndRecommendationKey(ACCOUNT_ID, "unused-ebs-vol-test123"))
                .thenReturn(Optional.empty());
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of());
        when(recommendationRepository.save(any(AwsRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AwsRecommendationServiceImpl recommendationService = new AwsRecommendationServiceImpl(sessionService,
                billingRepository, recommendationRepository, resolver, resourceService);

        AwsRecommendationResponse response = recommendationService.generateRecommendations("token", "2026-09");

                assertThat(response.totalSpend()).isEqualByComparingTo("0.19");
                assertThat(response.recommendations()).hasSize(2);
                assertThat(response.recommendations()).anySatisfy(item -> {
                        assertThat(item.resourceId()).isEqualTo("i-test123");
                        assertThat(item.problem()).isEqualTo("Stopped instance still incurred cost");
                });
                assertThat(response.recommendations()).anySatisfy(item -> {
                        assertThat(item.resourceId()).isEqualTo("vol-test123");
                        assertThat(item.problem()).isEqualTo("Unattached volume is incurring cost");
                        assertThat(item.currentCost()).isEqualByComparingTo("0.16");
                });
    }

    @Test
    void missingResourceIdColumnIsReportedAsMissingAndNeverInvented() throws Exception {
        String csv = "line_item_usage_account_id,line_item_usage_start_date,line_item_unblended_cost\n"
                + ACCOUNT_ID + ",2026-09-10T00:00:00Z,0.42\n";
        CurS3Service parser = new CurS3Service(org.mockito.Mockito.mock(AwsClientFactory.class), syncProperties);
        Method parse = CurS3Service.class.getDeclaredMethod("parse", byte[].class, String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CurS3Service.Row> parsedRows = (List<CurS3Service.Row>) parse.invoke(parser,
                csv.getBytes(StandardCharsets.UTF_8), "part-without-resource-id.csv");
        when(syncProperties.isEnabled()).thenReturn(true);
        when(s3Service.readLatest("access", "secret"))
                .thenReturn(new CurS3Service.Export("part-without-resource-id.csv",
                        Instant.parse("2026-09-11T00:00:00Z"), parsedRows));
        when(billingRepository.findByRecordKey(anyString())).thenReturn(Optional.empty());
        when(summaryRepository.findByAwsAccountIdAndBillingPeriod(eq(ACCOUNT_ID), anyString()))
                .thenReturn(Optional.empty());

        new CurBillingImportService(s3Service, billingRepository, summaryRepository, syncProperties)
                .importLatest("access", "secret", ACCOUNT_ID);

        ArgumentCaptor<AwsBilling> savedBilling = ArgumentCaptor.forClass(AwsBilling.class);
        verify(billingRepository).save(savedBilling.capture());
        assertThat(savedBilling.getValue().getCostAmount()).isEqualByComparingTo("0.42");
        assertThat(savedBilling.getValue().getResourceId()).isNull();
    }

        @Test
        void reimportRefreshesPreviouslyMissingRegionMapping() {
                LocalDate start = LocalDate.parse("2026-09-10");
                CurS3Service.Row curRow = new CurS3Service.Row(1, java.util.Map.ofEntries(
                        java.util.Map.entry("line_item_usage_account_id", ACCOUNT_ID),
                        java.util.Map.entry("line_item_usage_start_date", "2026-09-10T00:00:00Z"),
                        java.util.Map.entry("line_item_usage_end_date", "2026-09-10T01:00:00Z"),
                        java.util.Map.entry("line_item_unblended_cost", "0.03"),
                        java.util.Map.entry("line_item_usage_amount", "1"),
                        java.util.Map.entry("line_item_currency_code", "USD"),
                        java.util.Map.entry("line_item_resource_id", "i-existing"),
                        java.util.Map.entry("line_item_line_item_type", "Usage"),
                        java.util.Map.entry("line_item_product_code", "AmazonEC2"),
                        java.util.Map.entry("product_product_name", "Amazon Elastic Compute Cloud"),
                        java.util.Map.entry("product_region_code", "eu-west-1")));
                AwsBilling existing = new AwsBilling(ACCOUNT_ID, start, start.plusDays(1),
                        "Amazon Elastic Compute Cloud", "Global / No Region", BigDecimal.ONE, new BigDecimal("0.03"),
                        "USD", false, "Amazon Elastic Compute Cloud", "BoxUsage:t3.micro", "i-existing", "Usage",
                        start, start.plusDays(1), "existing-record-key", "CUR_2_0");
                when(syncProperties.isEnabled()).thenReturn(true);
                when(s3Service.readLatest("access", "secret"))
                        .thenReturn(new CurS3Service.Export("revised.csv", Instant.parse("2026-09-12T00:00:00Z"), List.of(curRow)));
                when(billingRepository.findByRecordKey(anyString())).thenReturn(Optional.of(existing));
                when(summaryRepository.findByAwsAccountIdAndBillingPeriod(eq(ACCOUNT_ID), anyString()))
                        .thenReturn(Optional.empty());

                new CurBillingImportService(s3Service, billingRepository, summaryRepository, syncProperties)
                        .importLatest("access", "secret", ACCOUNT_ID);

                assertThat(existing.getRegion()).isEqualTo("eu-west-1");
                verify(billingRepository).save(existing);
        }

        @Test
        void populatedResourceIdIsNotSkippedAsDuplicateOfPriorBlankIdRow() {
                when(syncProperties.isEnabled()).thenReturn(true);
                CurS3Service.Row oldRow = row(1, "");
                CurS3Service.Row updatedRow = row(1, "i-test123");
                when(s3Service.readLatest("access", "secret"))
                                .thenReturn(new CurS3Service.Export("old.csv", Instant.parse("2026-09-11T00:00:00Z"), List.of(oldRow)))
                                .thenReturn(new CurS3Service.Export("new.csv", Instant.parse("2026-09-12T00:00:00Z"), List.of(updatedRow)));
                Map<String, AwsBilling> existingRows = new HashMap<>();
                when(billingRepository.findByRecordKey(anyString())).thenAnswer(invocation ->
                    Optional.ofNullable(existingRows.get(invocation.getArgument(0))));
                when(billingRepository.save(any(AwsBilling.class))).thenAnswer(invocation -> {
                        AwsBilling billing = invocation.getArgument(0);
                        existingRows.put(billing.getRecordKey(), billing);
                        return billing;
                });
                when(summaryRepository.findByAwsAccountIdAndBillingPeriod(eq(ACCOUNT_ID), anyString())).thenReturn(Optional.empty());
                CurBillingImportService importer = new CurBillingImportService(s3Service, billingRepository, summaryRepository, syncProperties);

                importer.importLatest("access", "secret", ACCOUNT_ID);
                importer.importLatest("access", "secret", ACCOUNT_ID);

                ArgumentCaptor<AwsBilling> persisted = ArgumentCaptor.forClass(AwsBilling.class);
                verify(billingRepository, org.mockito.Mockito.times(2)).save(persisted.capture());
                assertThat(persisted.getAllValues()).extracting(AwsBilling::getResourceId).containsExactly((String) null, "i-test123");
                assertThat(persisted.getAllValues().get(0).getRecordKey())
                                .isNotEqualTo(persisted.getAllValues().get(1).getRecordKey());
        }

        @Test
        void correctedCurRegionAndResourceRefreshTheExistingLegacyBillingRow() {
                when(syncProperties.isEnabled()).thenReturn(true);
                CurS3Service.Row oldRow = row(1, "");
                Map<String, String> revisedValues = new HashMap<>(oldRow.values());
                revisedValues.put("identity_line_item_id", "line-item-123");
                revisedValues.put("line_item_resource_id", "i-corrected");
                revisedValues.put("product_region", "eu-west-1");
                CurS3Service.Row revisedRow = new CurS3Service.Row(1, revisedValues);
                when(s3Service.readLatest("access", "secret"))
                        .thenReturn(new CurS3Service.Export("old.csv", Instant.parse("2026-09-11T00:00:00Z"), List.of(oldRow)))
                        .thenReturn(new CurS3Service.Export("revised.csv", Instant.parse("2026-09-12T00:00:00Z"), List.of(revisedRow)));
                List<AwsBilling> stored = new ArrayList<>();
                when(billingRepository.findByRecordKey(org.mockito.ArgumentMatchers.anyString()))
                        .thenAnswer(invocation -> stored.stream()
                                .filter(item -> item.getRecordKey().equals(invocation.getArgument(0, String.class)))
                                .findFirst());
                when(billingRepository.save(org.mockito.ArgumentMatchers.isA(AwsBilling.class))).thenAnswer(invocation -> {
                        AwsBilling saved = invocation.getArgument(0);
                        stored.removeIf(item -> item.getId().equals(saved.getId()));
                        stored.add(saved);
                        return saved;
                });
                when(summaryRepository.findByAwsAccountIdAndBillingPeriod(eq(ACCOUNT_ID), anyString()))
                        .thenReturn(Optional.empty());
                CurBillingImportService importer = new CurBillingImportService(
                        s3Service, billingRepository, summaryRepository, syncProperties);

                importer.importLatest("access", "secret", ACCOUNT_ID);
                AwsBilling original = stored.get(0);
                when(billingRepository.findPotentialCurRevision(eq(ACCOUNT_ID), eq(LocalDate.parse("2026-09-10")),
                        eq(LocalDate.parse("2026-09-10")), eq("Amazon Elastic Compute Cloud"),
                        eq("BoxUsage:t3.micro"), eq("Usage"), eq(BigDecimal.ONE), eq(new BigDecimal("0.03")), eq("USD")))
                        .thenReturn(List.of(original));

                importer.importLatest("access", "secret", ACCOUNT_ID);

                assertThat(stored).singleElement().satisfies(billing -> {
                        assertThat(billing.getRegion()).isEqualTo("eu-west-1");
                        assertThat(billing.getResourceId()).isEqualTo("i-corrected");
                        assertThat(billing.getCostAmount()).isEqualByComparingTo("0.03");
                });
                verify(billingRepository, org.mockito.Mockito.times(2))
                        .save(org.mockito.ArgumentMatchers.isA(AwsBilling.class));
        }

                    @Test
                    void syncSurfacesMissingBucketConfigurationAsClientErrorReason() {
                        when(syncProperties.isEnabled()).thenReturn(true);
                        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                                "access", "secret", "ap-south-1", ACCOUNT_ID, Instant.now().plusSeconds(600)));
                        when(curBillingImportService.importLatest("access", "secret", ACCOUNT_ID))
                                .thenThrow(new IllegalStateException("AWS_BILLING_S3_BUCKET is not configured."));
                        AwsBillingSyncServiceImpl syncService = new AwsBillingSyncServiceImpl(sessionService, billingRepository,
                                summaryRepository, syncProperties, curBillingImportService);

                        assertThatThrownBy(() -> syncService.syncBilling("token"))
                                .isInstanceOf(AwsConnectionException.class)
                                .hasMessage("AWS_BILLING_S3_BUCKET is not configured.");
                    }

        @Test
        void existingSyncPostHandlerAcceptsConnectionTokenWithoutRequestBody() throws Exception {
                AtomicReference<String> receivedToken = new AtomicReference<>();
                AwsBillService billService = new AwsBillService() {
                        @Override public com.cloudcostai.aws.dto.response.AwsBillSummaryResponse getBillSummary(String token) { return null; }
                        @Override public com.cloudcostai.aws.dto.response.AwsBillSummaryResponse syncBilling(String token) {
                                receivedToken.set(token);
                                return null;
                        }
                        @Override public List<com.cloudcostai.aws.dto.response.AwsBillingServiceCostResponse> getServiceBreakdown(String token) { return List.of(); }
                };
                AwsBillingController controller = new AwsBillingController(billService);
                var method = AwsBillingController.class.getMethod("syncBilling", String.class);

                var response = controller.syncBilling("active-aws-token");

                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(receivedToken.get()).isEqualTo("active-aws-token");
                assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                                .containsExactly("/sync");
                assertThat(method.getParameterAnnotations()[0][0])
                                .isInstanceOf(org.springframework.web.bind.annotation.RequestHeader.class);
                assertThat(((org.springframework.web.bind.annotation.RequestHeader) method.getParameterAnnotations()[0][0]).value())
                                .isEqualTo("X-AWS-Connection-Token");
        }

        private CurS3Service.Row row(long number, String resourceId) {
                return new CurS3Service.Row(number, java.util.Map.ofEntries(
                                java.util.Map.entry("line_item_usage_account_id", ACCOUNT_ID),
                                java.util.Map.entry("line_item_unblended_cost", "0.03"),
                                java.util.Map.entry("line_item_usage_start_date", "2026-09-10T00:00:00Z"),
                                java.util.Map.entry("line_item_usage_end_date", "2026-09-10T01:00:00Z"),
                                java.util.Map.entry("line_item_currency_code", "USD"),
                                java.util.Map.entry("product_product_name", "Amazon Elastic Compute Cloud"),
                                java.util.Map.entry("product_region", "ap-south-1"),
                                java.util.Map.entry("line_item_usage_amount", "1"),
                                java.util.Map.entry("line_item_usage_type", "BoxUsage:t3.micro"),
                                java.util.Map.entry("line_item_resource_id", resourceId),
                                java.util.Map.entry("line_item_line_item_type", "Usage")));
        }

        private AwsResourceResponse stoppedInstanceAndUnattachedVolume() {
        return new AwsResourceResponse(ACCOUNT_ID, "ap-south-1",
                                new AwsResourceResponse.AwsResourceSummary(2, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0),
                List.of(new AwsResourceResponse.Ec2InstanceDto("i-test123", "test", "t3.micro", "stopped",
                        "ap-south-1a", "ap-south-1", null, null, null, List.of(), null)),
                                List.of(new AwsResourceResponse.EbsVolumeDto("vol-test123", "gp3", 30, "available",
                                                "ap-south-1a", null)), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}