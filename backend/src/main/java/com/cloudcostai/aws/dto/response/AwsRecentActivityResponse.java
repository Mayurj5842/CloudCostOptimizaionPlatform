package com.cloudcostai.aws.dto.response;

import java.time.Instant;
import java.util.List;

public record AwsRecentActivityResponse(List<Activity> activities) {
    public record Activity(
            String id,
            String service,
            String action,
            String message,
            String resourceId,
            String region,
            Instant timestamp
    ) {}
}
