package com.cloudcostai.aws.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.config.AwsClientFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Service
public class CurS3Service {
    private static final Logger logger = LoggerFactory.getLogger(CurS3Service.class);
    private final AwsClientFactory clientFactory;
    private final AwsBillingSyncProperties properties;

    public CurS3Service(AwsClientFactory clientFactory, AwsBillingSyncProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    public static Set<String> parseManifestKeys(byte[] manifest, ObjectMapper objectMapper, String bucket, String prefix) {
        try {
            JsonNode root = objectMapper.readTree(manifest);
            Set<String> keys = new LinkedHashSet<>();
            collectFileLocations(root.path("fileLocations"), bucket, prefix, keys);
            collectReportKeys(root.path("reportKeys"), prefix, keys);
            if (keys.isEmpty()) throw new IllegalStateException("Manifest contains no supported data-file references.");
            return keys;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse CUR manifest.", exception);
        }
    }

    private static void collectFileLocations(JsonNode locations, String bucket, String prefix, Set<String> keys) {
        if (!locations.isArray()) return;
        for (JsonNode location : locations) {
            String value = location.path("fileLocation").asText("");
            if (value.isBlank()) continue;
            if (value.startsWith("s3://")) {
                int separator = value.indexOf('/', 5);
                if (separator < 0 || !value.substring(5, separator).equals(bucket)) continue;
                value = value.substring(separator + 1);
            }
            addSupportedKey(value, prefix, keys);
        }
    }

    private static void collectReportKeys(JsonNode reportKeys, String prefix, Set<String> keys) {
        if (!reportKeys.isArray()) return;
        for (JsonNode key : reportKeys) addSupportedKey(key.asText(""), prefix, keys);
    }

    private static void addSupportedKey(String value, String prefix, Set<String> keys) {
        if (value.isBlank()) return;
        String normalizedPrefix = prefix == null ? "" : prefix;
        String key = value.startsWith(normalizedPrefix) ? value : normalizedPrefix + value;
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".csv") || lowerKey.endsWith(".csv.gz") || lowerKey.endsWith(".gz")) keys.add(key);
    }

    public Export readLatest(String accessKey, String secretKey) {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException("AWS_BILLING_S3_BUCKET is not configured.");
        }
        if (properties.getPrefix() == null || properties.getPrefix().isBlank()) {
            throw new IllegalStateException("AWS_BILLING_S3_PREFIX is not configured.");
        }
        logger.info("Searching S3 for CUR export bucket={} prefix={}", properties.getBucket(), properties.getPrefix());
        try (S3Client s3 = clientFactory.createS3Client(accessKey, secretKey, properties.getRegion())) {
            List<S3Object> objects = s3.listObjectsV2(ListObjectsV2Request.builder()
                            .bucket(properties.getBucket()).prefix(properties.getPrefix()).build())
                .contents();
            List<S3Object> manifests = objects.stream()
                .filter(object -> isManifest(object.key()))
                    .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                    .toList();
            if (!manifests.isEmpty()) {
            S3Object manifest = manifests.get(0);
            Set<String> dataKeys = parseManifestKeys(getObjectBytes(s3, manifest.key()), new ObjectMapper(),
                properties.getBucket(), properties.getPrefix());
            List<Row> rows = new ArrayList<>();
            for (String dataKey : dataKeys) rows.addAll(parse(getObjectBytes(s3, dataKey), dataKey));
            logger.info("CUR manifest selected: {} dataFiles={} rows={}", manifest.key(), dataKeys.size(), rows.size());
            return new Export(manifest.key(), manifest.lastModified(), List.copyOf(rows));
            }
            List<S3Object> csvFiles = objects.stream()
                .filter(object -> isCsv(object.key()))
                .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                .toList();
            if (csvFiles.isEmpty()) throw new IllegalStateException("No CUR CSV files are available under the configured S3 prefix.");
            S3Object object = csvFiles.get(0);
            logger.info("CUR file found: {}", object.key());
            return new Export(object.key(), object.lastModified(), parse(getObjectBytes(s3, object.key()), object.key()));
        } catch (RuntimeException exception) {
            logger.error("CUR import failed while reading S3: {}", exception.getMessage());
            throw exception;
        }
    }

    private List<Row> parse(byte[] bytes, String key) {
        try (InputStream input = key.toLowerCase(Locale.ROOT).endsWith(".gz")
                ? new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))
                : new java.io.ByteArrayInputStream(bytes);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build().parse(reader)) {
            Map<String, Integer> headers = parser.getHeaderMap().keySet().stream()
                    .collect(java.util.stream.Collectors.toMap(this::normalize, parser.getHeaderMap()::get, (left, right) -> left));
                logger.info("CUR file resource-id column present={}", headers.containsKey("line_item_resource_id"));
            require(headers, "line_item_usage_start_date", "line_item_usage_start_date");
            require(headers, "line_item_unblended_cost", "line_item_unblended_cost");
            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) rows.add(new Row(record.getRecordNumber(), values(record, headers)));
            return rows;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid CUR CSV file " + key + ": " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> values(CSVRecord record, Map<String, Integer> headers) {
        return headers.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue() < record.size() ? record.get(entry.getValue()).trim() : ""));
    }

    private void require(Map<String, Integer> headers, String key, String display) {
        if (!headers.containsKey(key)) throw new IllegalStateException("CUR file is missing required column " + display);
    }

    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace('.', '_'); }
    private boolean isManifest(String key) {
        String value = key.toLowerCase(Locale.ROOT);
        return value.contains("manifest") && value.endsWith(".json");
    }
    private boolean isCsv(String key) { String value = key.toLowerCase(Locale.ROOT); return value.endsWith(".csv") || value.endsWith(".csv.gz") || value.endsWith(".gz"); }
    private byte[] getObjectBytes(S3Client s3, String key) {
        ResponseBytes<?> bytes = s3.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build(),
                ResponseTransformer.toBytes());
        return bytes.asByteArray();
    }

    public record Export(String key, Instant lastModified, List<Row> rows) {}
    public record Row(long number, Map<String, String> values) {}
}
