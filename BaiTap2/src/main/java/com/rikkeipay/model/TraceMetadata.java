package com.rikkeipay.model;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Record TraceMetadata chứa các thuộc tính định danh phân loại chi phí LLM
 * sẵn sàng chuyển đổi nhúng vào metadata của Trace trên Langfuse.
 */
public record TraceMetadata(
    String department,
    String environment,
    String userId,
    String sessionId,
    String model,
    BigDecimal estimatedCost
) {

    /**
     * Chuyển đổi đối tượng TraceMetadata sang Map cấu trúc key-value.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("department", department);
        map.put("environment", environment);
        map.put("userId", userId);
        map.put("sessionId", sessionId);
        map.put("model", model);
        map.put("estimatedCostUsd", estimatedCost != null ? estimatedCost.toPlainString() : "0.00000000");
        return map;
    }

    /**
     * Chuyển đổi TraceMetadata sang chuỗi JSON String thuần Java.
     */
    public String toJsonString() {
        String costStr = estimatedCost != null ? estimatedCost.toPlainString() : "0.00000000";
        return String.format(
            "{\"department\":\"%s\",\"environment\":\"%s\",\"userId\":\"%s\",\"sessionId\":\"%s\",\"model\":\"%s\",\"estimatedCostUsd\":\"%s\"}",
            department, environment, userId, sessionId, model, costStr
        );
    }
}
