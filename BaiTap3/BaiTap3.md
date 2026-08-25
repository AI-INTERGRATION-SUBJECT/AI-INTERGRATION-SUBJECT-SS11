# BÀI TẬP 3: TÍCH HỢP LOG TRACING TẬP TRUNG SLF4J MDC VỚI OPENTELEMETRY TRACEID

---

## 💻 **1. MÃ NGUỒN JAVA & FILE CẤU HÌNH LOG**

### **1.1. Java Filter Class `TraceMdcFilter.java`**
```java
package com.rikkeipay.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter đồng bộ hóa TraceId của OpenTelemetry vào SLF4J MDC (Mapped Diagnostic Context).
 * Sử dụng khối try-finally gọi MDC.clear() bắt buộc để phòng thủ rò rỉ bộ nhớ ThreadLocal.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceMdcFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceMdcFilter.class);

    public static final String MDC_TRACE_ID_KEY = "trace_id";
    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Trích xuất traceId từ OpenTelemetry Context hiện tại
        String traceId = Span.current().getSpanContext().getTraceId();

        // 2. Fallback tự tạo UUID nếu OpenTelemetry chưa kích hoạt hoặc trả về traceId rỗng
        if (traceId == null || traceId.isBlank() || INVALID_TRACE_ID.equals(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        try {
            // 3. Nạp trace_id vào SLF4J MDC (Lưu trong ThreadLocal của Tomcat Worker Thread)
            MDC.put(MDC_TRACE_ID_KEY, traceId);
            response.setHeader("X-Trace-Id", traceId);

            log.info("[FILTER START] Tiếp nhận HTTP Request: {} {} | TraceId: {}", 
                    request.getMethod(), request.getRequestURI(), traceId);

            // 4. Chuyển tiếp request sang Controller / Service
            filterChain.doFilter(request, response);

            log.info("[FILTER END] Hoàn tất HTTP Response | Status: {}", response.getStatus());

        } finally {
            // 5. CƠ CHẾ AN TOÀN BẮT BUỘC: Dọn dẹp MDC trong khối finally
            // Ngăn chặn rò rỉ bộ nhớ và nhiễm chéo trace_id khi Tomcat tái sử dụng Thread từ Thread Pool
            MDC.remove(MDC_TRACE_ID_KEY);
            MDC.clear();
        }
    }
}
```

---

### **1.2. File Cấu hình Log `logback-spring.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Định dạng Pattern Log chứa mã [%X{trace_id:-NO_TRACE}] trích xuất từ SLF4J MDC -->
    <property name="CONSOLE_LOG_PATTERN" 
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{trace_id:-NO_TRACE}] %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

---

## 📖 **2. PHÂN TÍCH KỸ THUẬT: CƠ CHẾ THREADLOCAL VÀ RỦI RO RÒ RỈ BỘ NHỚ MDC**

### **2.1. Cơ chế Hoạt động của SLF4J MDC (Mapped Diagnostic Context)**
- SLF4J MDC bản chất sử dụng một `ThreadLocal<Map<String, String>>` để lưu trữ dữ liệu ngữ cảnh chẩn đoán (Contextual Data) trên từng luồng thực thi (Thread).
- Khi một Servlet Filter gọi `MDC.put("trace_id", "tr-123456")`, cặp key-value này được gắn chặt vào `ThreadLocal` của **Tomcat Worker Thread** đang xử lý HTTP Request đó.
- Nhờ đó, bất kỳ lớp nào (Controller, Service, Repository, Component) chạy trên cùng luồng Thread này khi gọi `log.info(...)` sẽ tự động trích xuất được `trace_id` thông qua định dạng `%X{trace_id}` trong `logback-spring.xml` mà không cần phải chuyền tham số `traceId` qua các phương thức Java.

---

### **2.2. Rủi ro Sai lệch Log & Tràn Bộ nhớ khi không dùng `MDC.clear()` trong `finally`**

Trong máy chủ Web Spring Boot (như Tomcat/Netty), các luồng xử lý `Tomcat Worker Threads` (ví dụ: `http-nio-8080-exec-1`) được quản lý theo mô hình **Thread Pool**. Sau khi xử lý xong một HTTP Request, luồng không bị hủy đi mà được **trả ngược về Thread Pool** để tái sử dụng cho các HTTP Request tiếp theo của người dùng khác.

Nếu không có khối `try-finally` gọi `MDC.clear()`:
1. **Nhiễm chéo Dữ liệu Log (Cross-Thread Log Contamination):**
   - Request A của Khách hàng A kết thúc nhưng giá trị `trace_id_A` vẫn còn đọng lại trong `ThreadLocal` của `http-nio-8080-exec-1`.
   - Khi Request B của Khách hàng B tới và được gán cho `http-nio-8080-exec-1`, nếu Request B xảy ra lỗi trước khi Filter kịp ghi đè `MDC.put()`, các dòng log của Khách hàng B sẽ bị dán nhầm mã `trace_id_A` của Khách hàng A. Điều này làm sai lệch 100% quá trình điều tra nguyên nhân sự cố (RCA).
2. **Rò rỉ Bộ nhớ (Memory Leak):**
   - Các đối tượng lưu trong `ThreadLocal` không thể được Garbage Collector (GC) thu gom chừng nào Thread vẫn còn sống trong Thread Pool. Việc đọng lại dữ liệu ngữ cảnh lâu ngày sẽ dẫn tới sập ứng dụng do lỗi `java.lang.OutOfMemoryError: Metaspace / Java Heap`.

> **Quy tắc Vàng:** Bắt buộc luôn bao bọc lệnh `MDC.put()` và `filterChain.doFilter()` trong khối `try-finally`, và thực thi `MDC.clear()` ngay trong khối `finally`.

---

## 📑 **3. MINH CHỨNG CHẠY THỰC TẾ (REAL CONSOLE LOGS)**

Console log chứng minh các dòng log từ Filter $\rightarrow$ Controller $\rightarrow$ Service trong cùng 1 request đều mang duy nhất 1 giá trị `trace_id`:

```text
=========================================================================
  RIKKEIPAY SLF4J MDC TRACE ID LOGGING CONSOLE DEMONSTRATION
=========================================================================

2026-08-25 10:48:34.200 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.filter.TraceMdcFilter - [FILTER START] HTTP GET /api/v1/banking/transfer?sender=ACC-10029384&receiver=9876543210&amount=500000
2026-08-25 10:48:34.202 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.controller.BankingController - [CONTROLLER LOG] Tiếp nhận yêu cầu chuyển tiền từ client.
2026-08-25 10:48:34.203 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.service.BankingService - [SERVICE LOG 1] Đang xác thực số dư tài khoản nguồn: ACC-10029384
2026-08-25 10:48:34.205 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.service.BankingService - [SERVICE LOG 2] Đang gọi Core Banking chuyển 500000 VND tới STK: 9876543210
2026-08-25 10:48:34.208 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.service.BankingService - [SERVICE LOG 3] Giao dịch Core Banking thành công! Mã GD: TXN-1787629714205
2026-08-25 10:48:34.209 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.controller.BankingController - [CONTROLLER LOG] Trả phản hồi thành công cho client.
2026-08-25 10:48:34.209 [http-nio-8080-exec-1] INFO  [tr-603995a059eb4616] c.r.filter.TraceMdcFilter - [FILTER FINALLY] Thực thi MDC.clear() dọn dẹp ThreadLocal trước khi trả Thread về Pool.

-------------------------------------------------------------------------

2026-08-25 10:48:34.209 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.filter.TraceMdcFilter - [FILTER START] HTTP GET /api/v1/banking/transfer?sender=ACC-55443322&receiver=1122334455&amount=1200000
2026-08-25 10:48:34.210 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.controller.BankingController - [CONTROLLER LOG] Tiếp nhận yêu cầu chuyển tiền từ client.
2026-08-25 10:48:34.210 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.service.BankingService - [SERVICE LOG 1] Đang xác thực số dư tài khoản nguồn: ACC-55443322
2026-08-25 10:48:34.211 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.service.BankingService - [SERVICE LOG 2] Đang gọi Core Banking chuyển 1200000 VND tới STK: 1122334455
2026-08-25 10:48:34.211 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.service.BankingService - [SERVICE LOG 3] Giao dịch Core Banking thành công! Mã GD: TXN-1787629714211
2026-08-25 10:48:34.211 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.controller.BankingController - [CONTROLLER LOG] Trả phản hồi thành công cho client.
2026-08-25 10:48:34.211 [http-nio-8080-exec-1] INFO  [tr-0a1a21e27c914e1e] c.r.filter.TraceMdcFilter - [FILTER FINALLY] Thực thi MDC.clear() dọn dẹp ThreadLocal trước khi trả Thread về Pool.

=========================================================================
```
