package com.rikkeipay.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

public class TraceMdcTestRunner {

    private static final Logger log = LoggerFactory.getLogger(TraceMdcTestRunner.class);

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("   RIKKEIPAY SLF4J MDC TRACE ID LOGGING DEMONSTRATION");
        System.out.println("=========================================================================\n");

        // Giả lập Request 1 trên Tomcat Worker Thread 1
        simulateHttpRequest("req-001", "ACC-10029384", "9876543210", "500000");

        System.out.println("\n-------------------------------------------------------------------------\n");

        // Giả lập Request 2 trên cùng Worker Thread 1 (Tái sử dụng Thread từ Thread Pool)
        simulateHttpRequest("req-002", "ACC-55443322", "1122334455", "1200000");

        System.out.println("\n=========================================================================");
    }

    private static void simulateHttpRequest(String reqId, String sender, String receiver, String amount) {
        String generatedTraceId = "tr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            // 1. Filter nạp trace_id vào MDC ThreadLocal
            MDC.put("trace_id", generatedTraceId);
            log.info("[FILTER START] HTTP GET /api/v1/banking/transfer?sender={}&receiver={}&amount={}", sender, receiver, amount);

            // 2. Controller tiếp nhận request
            log.info("[CONTROLLER] Controller xử lý giao dịch cho Request ID: {}", reqId);

            // 3. Service xử lý nghiệp vụ
            log.info("[SERVICE 1] Đang xác thực số dư tài khoản nguồn: {}", sender);
            log.info("[SERVICE 2] Đang chuyển {} VND tới tài khoản nhận: {}", amount, receiver);
            log.info("[SERVICE 3] Giao dịch Core Banking thành công! Mã GD: TXN-{}", System.currentTimeMillis());

            // 4. Controller trả phản hồi
            log.info("[CONTROLLER] Trả HTTP 200 OK phản hồi thành công.");

        } finally {
            // 5. Khối finally giải phóng MDC ThreadLocal bắt buộc
            MDC.remove("trace_id");
            MDC.clear();
            log.info("[FILTER CLEANUP] Khối finally đã xóa MDC trace_id thành công! (MDC size = {})", MDC.getCopyOfContextMap() == null ? 0 : MDC.getCopyOfContextMap().size());
        }
    }
}
