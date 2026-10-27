package com.cloudcostai.aws.model;

/**
 * Canonical handling of the {@code resource_id} columns.
 *
 * <p>Both {@code aws_billing.resource_id} and {@code aws_recommendations.resource_id} must always hold a value so the
 * columns are never NULL. Rows whose source data has no resource identifier (Cost Explorer aggregates, Tax, Data
 * Transfer, service-level recommendations, ...) store {@link #NONE} instead of a real identifier.
 *
 * <p>{@link #isPresent(String)} is the single place that decides whether a stored value identifies a real resource, so
 * matching and availability logic keeps treating {@link #NONE} as "no resource" exactly as it treated NULL before.
 */
public final class ResourceIds {
    /** Placeholder stored when the source data has no resource identifier. Never a real resource id. */
    public static final String NONE = "N/A";

    private ResourceIds() {
    }

    /** True when {@code value} identifies a real resource, i.e. it is not null, blank, or the {@link #NONE} placeholder. */
    public static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !NONE.equals(value.trim());
    }

    /** Returns the value to persist: the trimmed identifier when present, otherwise {@link #NONE}. Never null. */
    public static String store(String value) {
        return isPresent(value) ? value.trim() : NONE;
    }
}
