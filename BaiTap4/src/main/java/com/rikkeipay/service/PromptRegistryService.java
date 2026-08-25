package com.rikkeipay.service;

import com.rikkeipay.config.LangfuseConfig.LangfuseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý Prompt tập trung từ Langfuse Prompt Registry với kiến trúc 4 tầng:
 * Tầng 1: Remote Fetch từ Langfuse Registry Server (label: production)
 * Tầng 2: In-Memory Cache (TTL 60s) giảm latency xuống ~0ms
 * Tầng 3: Defensive Fallback Prompt bảo vệ hệ thống khi Langfuse Server offline
 * Tầng 4: Compile Variables thay thế các biến giữ chỗ động
 */
@Service
public class PromptRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistryService.class);

    private final LangfuseClient langfuseClient;
    private final ConcurrentHashMap<String, CachedPrompt> promptCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // TTL = 60 giây

    // Tầng 3: Fallback Prompt mặc định được định nghĩa sẵn trong Java Code
    public static final String DEFAULT_FALLBACK_PROMPT = """
        [DEFAULT HARDCODED FALLBACK PROMPT - EMERGENCY MODE]
        Bạn là Trợ lý ảo chuyển tiền RikkeiPay (Chế độ Dự phòng Khẩn cấp).
        Thông tin tài khoản:
        - Khách hàng: {{user_name}}
        - Số dư: {{current_balance}} VND
        - Chính sách: {{bank_policy}}
        
        Yêu cầu: {{user_input}}
        Xử lý yêu cầu chuyển khoản an toàn.
        """;

    public PromptRegistryService(LangfuseClient langfuseClient) {
        this.langfuseClient = langfuseClient;
    }

    private static class CachedPrompt {
        final String template;
        final long fetchedTimestamp;

        CachedPrompt(String template, long fetchedTimestamp) {
            this.template = template;
            this.fetchedTimestamp = fetchedTimestamp;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - fetchedTimestamp) > CACHE_TTL_MS;
        }
    }

    /**
     * Phương thức chính truy xuất và biên dịch Prompt 4 tầng an toàn.
     *
     * @param promptName Tên Prompt (Ví dụ: 'banking_transfer_prompt')
     * @param variables Map chứa các biến động (user_name, current_balance, bank_policy, user_input)
     * @return Chuỗi Prompt hoàn chỉnh đã biên dịch
     */
    public String getPrompt(String promptName, Map<String, Object> variables) {
        long startTime = System.currentTimeMillis();
        String rawTemplate;

        // TẦNG 2: IN-MEMORY CACHE CHECK
        CachedPrompt cached = promptCache.get(promptName);
        if (cached != null && !cached.isExpired()) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[CACHE HIT] Hit In-Memory Cache cho Prompt '{}' | Latency: {} ms (TTL 60s)", promptName, duration);
            rawTemplate = cached.template;
        } else {
            // TẦNG 1: REMOTE FETCH TỪ LANGFUSE REGISTRY (KÈM TẦNG 3: DEFENSIVE FALLBACK)
            try {
                log.info("[CACHE MISS / EXPIRED] Đang gửi HTTP Request tới Langfuse Registry cho Prompt '{}'...", promptName);
                rawTemplate = langfuseClient.getPromptFromRegistry(promptName, "production");

                long fetchDuration = System.currentTimeMillis() - startTime;
                log.info("[REMOTE FETCH SUCCESS] Nạp thành công từ Langfuse Registry | Latency: {} ms", fetchDuration);

                // Cập nhật bộ đệm Cache
                promptCache.put(promptName, new CachedPrompt(rawTemplate, System.currentTimeMillis()));

            } catch (Exception e) {
                // TẦNG 3: DEFENSIVE FALLBACK KHI LANGFUSE OFFLINE HOẶC LỖI KẾT NỐI
                log.warn("[LANGFUSE OFFLINE / TIMEOUT] Cảnh báo lỗi kết nối Registry: {} | Kích hoạt FALLBACK PROMPT!", e.getMessage());
                rawTemplate = DEFAULT_FALLBACK_PROMPT;
            }
        }

        // TẦNG 4: COMPILE VARIABLES THAY THẾ BIẾN ĐỘNG
        return compileVariables(rawTemplate, variables);
    }

    private String compileVariables(String template, Map<String, Object> variables) {
        if (template == null) return "";
        String compiled = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String valueStr = entry.getValue() != null ? entry.getValue().toString() : "";
                compiled = compiled.replace(placeholder, valueStr);
            }
        }
        return compiled;
    }

    public void clearCache() {
        promptCache.clear();
    }
}
