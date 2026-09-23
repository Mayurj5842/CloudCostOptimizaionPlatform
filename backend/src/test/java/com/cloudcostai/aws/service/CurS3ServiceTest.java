package com.cloudcostai.aws.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
