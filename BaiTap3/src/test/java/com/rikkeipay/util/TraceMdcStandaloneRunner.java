package com.rikkeipay.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standalone Runner mô phỏng cơ chế SLF4J MDC (ThreadLocal) và cấu hình Log Pattern.
 */
public class TraceMdcStandaloneRunner {

    // Mô phỏng SLF4J MDC bằng ThreadLocal
    private static final ThreadLocal<Map<String, String>> MDC_THREAD_LOCAL = ThreadLocal.withInitial(HashMap::new);

    public static void mdcPut(String key, String value) {
        MDC_THREAD_LOCAL.get().put(key, value);
    }

    public static String mdcGet(String key) {
        return MDC_THREAD_LOCAL.get().get(key);
    }

    public static void mdcClear() {
        MDC_THREAD_LOCAL.get().clear();
        MDC_THREAD_LOCAL.remove();
    }

    public static void log(String level, String loggerName, String message) {
        String traceId = mdcGet("trace_id");
        if (traceId == null || traceId.isBlank()) {
            traceId = "NO_TRACE";
        }
        String timestamp = java.time.LocalDateTime.now().toString().replace("T", " ");
        String threadName = Thread.currentThread().getName();
        
        System.out.printf("%s [%s] %-5s [%s] %s - %s%n", 
                timestamp, threadName, level, traceId, loggerName, message);
    }

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("  RIKKEIPAY SLF4J MDC TRACE ID LOGGING CONSOLE DEMONSTRATION");
        System.out.println("=========================================================================\n");

        // Giả lập Request 1 trên Tomcat Worker Thread 'http-nio-8080-exec-1'
        simulateRequest("http-nio-8080-exec-1", "ACC-10029384", "9876543210", "500000");

        System.out.println("\n-------------------------------------------------------------------------\n");

        // Giả lập Request 2 trên CÙNG Thread 'http-nio-8080-exec-1' (Tái sử dụng từ Thread Pool)
        simulateRequest("http-nio-8080-exec-1", "ACC-55443322", "1122334455", "1200000");

        System.out.println("\n=========================================================================");
    }

    private static void simulateRequest(String threadName, String sender, String receiver, String amount) {
        Thread.currentThread().setName(threadName);
        String generatedTraceId = "tr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            // 1. TraceMdcFilter nạp trace_id vào MDC
            mdcPut("trace_id", generatedTraceId);
            log("INFO", "c.r.filter.TraceMdcFilter", 
                    "[FILTER START] HTTP GET /api/v1/banking/transfer?sender=" + sender + "&receiver=" + receiver + "&amount=" + amount);

            // 2. Controller tiếp nhận request
            log("INFO", "c.r.controller.BankingController", 
                    "[CONTROLLER LOG] Tiếp nhận yêu cầu chuyển tiền từ client.");

            // 3. Service xử lý nghiệp vụ
            log("INFO", "c.r.service.BankingService", 
                    "[SERVICE LOG 1] Đang xác thực số dư tài khoản nguồn: " + sender);
            log("INFO", "c.r.service.BankingService", 
                    "[SERVICE LOG 2] Đang gọi Core Banking chuyển " + amount + " VND tới STK: " + receiver);
            log("INFO", "c.r.service.BankingService", 
                    "[SERVICE LOG 3] Giao dịch Core Banking thành công! Mã GD: TXN-" + System.currentTimeMillis());

            // 4. Controller trả phản hồi
            log("INFO", "c.r.controller.BankingController", 
                    "[CONTROLLER LOG] Trả phản hồi thành công cho client.");

        } finally {
            // 5. Khối finally giải phóng MDC ThreadLocal bắt buộc
            log("INFO", "c.r.filter.TraceMdcFilter", 
                    "[FILTER FINALLY] Thực thi MDC.clear() dọn dẹp ThreadLocal trước khi trả Thread về Pool.");
            mdcClear();
        }
    }
}
