package com.cloudcostai.aws.dto.response;

import java.time.Instant;
import java.util.List;

public record SystemDiagnosticsResponse(Instant checkedAt, List<DiagnosticItem> checks) {
    public record DiagnosticItem(String key, String label, String status, String detail) {
    }
}