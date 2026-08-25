package com.rikkeipay.util;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone Runner mô phỏng kiến trúc 4 tầng của PromptRegistryService:
 * Tầng 1: Remote Fetch từ Langfuse Registry Server
 * Tầng 2: In-Memory Cache (TTL 60s) ~0ms latency
 * Tầng 3: Defensive Fallback Prompt khi Server offline
 * Tầng 4: Compile Variables thay thế biến động
 */
public class PromptRegistryStandaloneRunner {

    // === MÔ PHỎNG LANGFUSE CLIENT ===
    static boolean langfuseOnline = true;

    static String fetchFromLangfuseRegistry(String promptName, String label) {
        if (!langfuseOnline) {
            throw new RuntimeException("Connection refused: http://localhost:3000 (Server Offline)");
        }
        return """
                [LANGFUSE REGISTRY PROMPT - PRODUCTION v2.1 - Tên: {{promptName}} | Label: production]
                Bạn là Giao dịch viên Ngân hàng số RikkeiPay Assistant chuyên nghiệp.
                Thông tin tài khoản:
                - Chủ tài khoản: {{user_name}}
                - Số dư khả dụng: {{current_balance}} VND
                - Chính sách ngân hàng: {{bank_policy}}
                
                Yêu cầu của khách: {{user_input}}
                Vui lòng xử lý an toàn và trả về kết quả JSON.
                """.replace("{{promptName}}", promptName);
    }

    // === MÔ PHỎNG DEFAULT FALLBACK PROMPT ===
    static final String DEFAULT_FALLBACK_PROMPT = """
            [DEFAULT HARDCODED FALLBACK PROMPT - EMERGENCY MODE]
            Bạn là Trợ lý ảo chuyển tiền RikkeiPay (Chế độ Dự phòng Khẩn cấp).
            Thông tin tài khoản:
            - Khách hàng: {{user_name}}
            - Số dư: {{current_balance}} VND
            - Chính sách: {{bank_policy}}
            
            Yêu cầu: {{user_input}}
            Xử lý yêu cầu chuyển khoản an toàn.
            """;

    // === MÔ PHỎNG IN-MEMORY CACHE (ConcurrentHashMap + TTL) ===
    record CachedPrompt(String template, long fetchedAt) {
        boolean isExpired() { return System.currentTimeMillis() - fetchedAt > 60_000L; }
    }
    static final ConcurrentHashMap<String, CachedPrompt> CACHE = new ConcurrentHashMap<>();

    // === LOG HELPER ===
    static void log(String level, String logger, String msg) {
        System.out.printf("%s [main] %-5s [%s] %s%n",
                LocalDateTime.now().toString().substring(0, 23), level, logger, msg);
    }

    // === PHƯƠNG THỨC CHÍNH 4 TẦNG ===
    static String getPrompt(String promptName, Map<String, Object> variables) {
        long start = System.currentTimeMillis();
        String rawTemplate;

        // TẦNG 2: CHECK IN-MEMORY CACHE
        CachedPrompt cached = CACHE.get(promptName);
        if (cached != null && !cached.isExpired()) {
            long latency = System.currentTimeMillis() - start;
            log("INFO ", "c.r.service.PromptRegistryService",
                    "[CACHE HIT ✅] Prompt '" + promptName + "' lấy từ In-Memory Cache | Latency: " + latency + " ms");
            rawTemplate = cached.template;
        } else {
            // TẦNG 1: REMOTE FETCH TỪ LANGFUSE REGISTRY
            try {
                log("INFO ", "c.r.service.PromptRegistryService",
                        "[CACHE MISS] Gửi HTTP Request tới Langfuse Registry Server...");
                rawTemplate = fetchFromLangfuseRegistry(promptName, "production");

                long fetchLatency = System.currentTimeMillis() - start;
                log("INFO ", "c.r.service.PromptRegistryService",
                        "[REMOTE FETCH SUCCESS ✅] Nạp từ Langfuse Registry | Latency: " + fetchLatency + " ms → Cập nhật Cache");

                // Cập nhật Cache (lưu kèm timestamp TTL)
                CACHE.put(promptName, new CachedPrompt(rawTemplate, System.currentTimeMillis()));

            } catch (Exception ex) {
                // TẦNG 3: DEFENSIVE FALLBACK KHI LANGFUSE OFFLINE
                log("WARN ", "c.r.service.PromptRegistryService",
                        "[LANGFUSE OFFLINE ⚠️] Lỗi kết nối Registry: " + ex.getMessage());
                log("WARN ", "c.r.service.PromptRegistryService",
                        "[FALLBACK ACTIVATED 🛡️] Kích hoạt DEFAULT_FALLBACK_PROMPT. Ứng dụng tiếp tục chạy bình thường!");
                rawTemplate = DEFAULT_FALLBACK_PROMPT;
            }
        }

        // TẦNG 4: COMPILE VARIABLES
        String compiled = rawTemplate;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                compiled = compiled.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }
        return compiled;
    }

    // === MAIN ===
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=========================================================================");
        System.out.println("  RIKKEIPAY PROMPT REGISTRY 4-LAYER RESILIENCE DEMONSTRATION");
        System.out.println("=========================================================================\n");

        Map<String, Object> vars = new HashMap<>();
        vars.put("user_name", "Nguyen Van A");
        vars.put("current_balance", "5,000,000");
        vars.put("bank_policy", "Miễn phí chuyển tiền liên ngân hàng");
        vars.put("user_input", "Chuyển 500k cho bạn Nam STK 9876543210 VCB");

        // =========== TRƯỜNG HỢP 1: LANGFUSE SERVER ONLINE ===========
        System.out.println("════════════ TRƯỜNG HỢP 1: MÁY CHỦ LANGFUSE ONLINE ════════════\n");
        langfuseOnline = true;

        System.out.println("─── LẦN GỌI 1: CACHE MISS → REMOTE FETCH TỪ LANGFUSE REGISTRY ───");
        String prompt1 = getPrompt("banking_transfer_prompt", vars);
        System.out.println("\n>>> Nội dung Prompt Đã Biên Dịch (Lần 1):\n" + prompt1);

        System.out.println("─── LẦN GỌI 2: CACHE HIT → IN-MEMORY CACHE LATENCY ~0ms ───");
        String prompt2 = getPrompt("banking_transfer_prompt", vars);
        System.out.println("\n>>> Nội dung Prompt Đã Biên Dịch (Lần 2 - từ Cache):\n" + prompt2);

        System.out.println("\n─────────────────────────────────────────────────────────────────\n");

        // =========== TRƯỜNG HỢP 2: LANGFUSE SERVER OFFLINE ===========
        System.out.println("════════════ TRƯỜNG HỢP 2: MÁY CHỦ LANGFUSE OFFLINE (SẬP SERVER) ════════════\n");
        langfuseOnline = false;
        CACHE.clear(); // Xóa cache để kiểm tra khả năng Fallback

        System.out.println("─── LẦN GỌI 3: SERVER OFFLINE → KÍCH HOẠT DEFENSIVE FALLBACK PROMPT ───");
        String fallbackPrompt = getPrompt("banking_transfer_prompt", vars);
        System.out.println("\n>>> Nội dung Prompt Dự phòng (Lần 3 - Fallback Emergency):\n" + fallbackPrompt);

        System.out.println("=========================================================================");
        System.out.println(" RESULT: HẠ TẦNG PROMPT REGISTRY 4 TẦNG HOẠT ĐỘNG BỀN VỮNG 100%!");
        System.out.println("         ✅ Cache TTL 60s giảm latency ~0ms");
        System.out.println("         ✅ Fallback Prompt bảo vệ hệ thống khi Langfuse Server sập");
        System.out.println("=========================================================================");
    }
}
