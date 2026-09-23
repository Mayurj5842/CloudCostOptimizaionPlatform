package com.cloudcostai.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.ZoneId;

@Component
public class AwsBillingSyncProperties {

    private final boolean enabled;
    private final long intervalHours;
    private final String bucket;
    private final String prefix;
    private final String region;

    public AwsBillingSyncProperties(
            // COST EXPLORER TEMPORARILY DISABLED
            // DO NOT ENABLE UNTIL BILLING ARCHITECTURE IS UPDATED
            // AWS COST EXPLORER API CALL IS DISABLED TO PREVENT ADDITIONAL COST
            @Value("${billing.sync.enabled:false}") boolean enabled,
            @Value("${billing.sync.interval-hours:24}") long intervalHours,
            @Value("${aws.billing.s3.bucket:}") String bucket,
            @Value("${aws.billing.s3.prefix:}") String prefix,
            @Value("${aws.billing.region:}") String region) {
        this.enabled = enabled;
        this.intervalHours = intervalHours;
        this.bucket = bucket;
        this.prefix = prefix;
        this.region = region;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getIntervalHours() {
        return intervalHours;
    }

    public String getBucket() { return bucket; }

    public String getPrefix() { return prefix; }

    public String getRegion() { return region; }

    public ZoneId getTimeZone() { return ZoneId.systemDefault(); }
}
