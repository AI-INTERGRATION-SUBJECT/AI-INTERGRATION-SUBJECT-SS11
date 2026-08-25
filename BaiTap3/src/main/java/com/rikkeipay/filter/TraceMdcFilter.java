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
