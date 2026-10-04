package com.cloudcostai.aws.service;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.config.AwsClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurS3ServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDataExportsManifestWithMultipleCsvAndGzipFiles() throws Exception {
        byte[] manifest = """
                {
                  "exportName": "MyCloudCostAiExports",
                  "fileLocations": [
                    {"fileLocation": "s3://cloudcostai-cur/MyCloudCostAiExports/data/part-000.csv.gz"},
                    {"fileLocation": "MyCloudCostAiExports/data/part-001.csv"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        Set<String> keys = CurS3Service.parseManifestKeys(manifest, objectMapper, "cloudcostai-cur", "MyCloudCostAiExports/");

        assertThat(keys).containsExactlyInAnyOrder(
                "MyCloudCostAiExports/data/part-000.csv.gz",
                "MyCloudCostAiExports/data/part-001.csv");
    }

    @Test
    void parsesLegacyReportKeysWithoutChangingTheirMeaning() throws Exception {
        byte[] manifest = "{\"reportKeys\":[\"data/part-000.csv.gz\",\"data/part-001.csv\"]}"
                .getBytes(StandardCharsets.UTF_8);

        Set<String> keys = CurS3Service.parseManifestKeys(manifest, objectMapper, "bucket", "prefix/");

        assertThat(keys).containsExactlyInAnyOrder("prefix/data/part-000.csv.gz", "prefix/data/part-001.csv");
    }

    @Test
    void rejectsManifestWithoutSupportedFileReferences() {
        byte[] manifest = "{\"columns\":[\"line_item_unblended_cost\"],\"metadata\":{}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CurS3Service.parseManifestKeys(manifest, objectMapper, "bucket", "prefix/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no supported data-file references");
    }

    @Test
    void rejectsMalformedManifest() {
        byte[] manifest = "{not-json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CurS3Service.parseManifestKeys(manifest, objectMapper, "bucket", "prefix/"))
                .isInstanceOf(Exception.class);
    }

        @Test
        void reportsMissingBucketConfigurationBeforeCreatingS3Client() {
                AwsBillingSyncProperties properties = mock(AwsBillingSyncProperties.class);
                when(properties.getBucket()).thenReturn("");
                when(properties.getPrefix()).thenReturn("exports/");

                assertThatThrownBy(() -> new CurS3Service(mock(AwsClientFactory.class), properties)
                                .readLatest("access", "secret"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("AWS_BILLING_S3_BUCKET is not configured.");
        }

        @Test
        void reportsMissingPrefixConfigurationBeforeCreatingS3Client() {
                AwsBillingSyncProperties properties = mock(AwsBillingSyncProperties.class);
                when(properties.getBucket()).thenReturn("bucket");
                when(properties.getPrefix()).thenReturn("");

                assertThatThrownBy(() -> new CurS3Service(mock(AwsClientFactory.class), properties)
                                .readLatest("access", "secret"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("AWS_BILLING_S3_PREFIX is not configured.");
        }

    @Test
    void readLatestLoadsEveryCsvPartFromNewestDataExportManifest() {
        AwsClientFactory factory = mock(AwsClientFactory.class);
        AwsBillingSyncProperties properties = mock(AwsBillingSyncProperties.class);
        S3Client s3 = mock(S3Client.class);
        when(properties.getBucket()).thenReturn("bucket");
        when(properties.getPrefix()).thenReturn("exports/");
        when(properties.getRegion()).thenReturn("us-east-1");
        when(factory.createS3Client("access", "secret", "us-east-1")).thenReturn(s3);
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        object("exports/manifest.json", "2026-09-10T00:00:00Z"),
                        object("exports/data/part-000.csv", "2026-09-10T00:00:01Z"),
                        object("exports/data/part-001.csv", "2026-09-10T00:00:02Z"))
                .build());
        Map<String, String> files = Map.of(
                "exports/manifest.json", "{\"fileLocations\":["
                        + "{\"fileLocation\":\"s3://bucket/exports/data/part-000.csv\"},"
                        + "{\"fileLocation\":\"s3://bucket/exports/data/part-001.csv\"}]}",
                "exports/data/part-000.csv", "line_item_resource_id,line_item_usage_start_date,line_item_unblended_cost\n"
                        + "i-test123,2026-09-10T00:00:00Z,0.03\n",
                "exports/data/part-001.csv", "line_item_resource_id,line_item_usage_start_date,line_item_unblended_cost\n"
                        + "vol-test123,2026-09-10T00:00:00Z,0.16\n");
        when(s3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            byte[] contents = files.get(request.key()).getBytes(StandardCharsets.UTF_8);
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), contents);
        });

        CurS3Service.Export export = new CurS3Service(factory, properties).readLatest("access", "secret");

        assertThat(export.key()).isEqualTo("exports/manifest.json");
        assertThat(export.rows()).extracting(row -> row.values().get("line_item_resource_id"))
                .containsExactlyInAnyOrder("i-test123", "vol-test123");
    }

    private S3Object object(String key, String modified) {
        return S3Object.builder().key(key).lastModified(Instant.parse(modified)).build();
    }
}
