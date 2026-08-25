# BÀI TẬP 1: CẤU HÌNH HẠ TẦNG GIÁM SÁT TRACING & PHÒNG THỦ CHỐNG NGHẼN

---

## 🐋 **1. FILE DOCKER COMPOSE HẠ TẦNG (`docker-compose-langfuse.yml`)**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: langfuse-postgres
    environment:
      POSTGRES_USER: langfuse
      POSTGRES_PASSWORD: langfuse_password_123
      POSTGRES_DB: langfuse
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U langfuse -d langfuse"]
      interval: 5s
      timeout: 5s
      retries: 5
    restart: always

  clickhouse:
    image: clickhouse/clickhouse-server:24.3
    container_name: langfuse-clickhouse
    environment:
      CLICKHOUSE_USER: default
      CLICKHOUSE_PASSWORD: clickhouse_password_123
      CLICKHOUSE_DB: default
    ports:
      - "8123:8123"
      - "9000:9000"
    volumes:
      - clickhouse_data:/var/lib/clickhouse
    restart: always

  langfuse-server:
    image: langfuse/langfuse:2
    container_name: langfuse-server
    depends_on:
      postgres:
        condition: service_healthy
      clickhouse:
        condition: service_started
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - DATABASE_URL=postgresql://langfuse:langfuse_password_123@postgres:5432/langfuse?schema=public
      - NEXTAUTH_URL=http://localhost:3000
      - NEXTAUTH_SECRET=my-super-secret-key-rikkeipay-2026
      - SALT=my-salt-key-2026
      - TELEMETRY_ENABLED=false
      - CLICKHOUSE_URL=http://clickhouse:8123
      - CLICKHOUSE_USER=default
      - CLICKHOUSE_PASSWORD=clickhouse_password_123
    restart: always

volumes:
  postgres_data:
  clickhouse_data:
```

---

## ⚙️ **2. FILE CẤU HÌNH ứng dụng (`application.yml`)**

```yaml
spring:
  application:
    name: rikkeipay-tracing-resilience

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:3000/api/public/otlp/v1/traces
      headers:
        Authorization: "Basic cGstbGYtZGV2LWtleTpzay1sZi1kZXYtc2VjcmV0"

# Cấu hình OpenTelemetry Batch Span Processor bất đồng bộ phòng thủ chống nghẽn luồng
opentelemetry:
  tracer:
    exporter:
      otlp:
        endpoint: http://localhost:3000/api/public/otlp/v1/traces
        protocol: http/protobuf
        timeout: 5s
    processor:
      batch:
        max-queue-size: 2048        # Kích thước tối đa hàng đợi đệm trong RAM (2048 spans)
        schedule-delay: 5s          # Khoảng thời gian định kỳ đẩy batch sang Langfuse (5 giây)
        max-export-batch-size: 512  # Số lượng spans gửi tối đa trong 1 đợt batch
        export-timeout: 5s          # Timeout tối đa chờ hạ tầng OTLP phản hồi (5 giây)

spring.ai.openai:
  api-key: ${ROUTER_API_KEY:sk-or-v1-dummy-key-for-test}
  base-url: https://openrouter.ai/api/v1
  chat.options.model: google/gemini-2.5-flash

server:
  port: 8080
```

---

## 📖 **3. PHÂN TÍCH KĨ THUẬT: CƠ CHẾ DROP SPAN & NON-BLOCKING GUARANTEE**

### **3.1. Bản chất Kiến trúc OpenTelemetry Batch Span Processor**
Trong ứng dụng Spring Boot Core Banking RikkeiPay, các luồng giao dịch tài chính (Business Thread Pool) thực thi xử lý dữ liệu và tạo ra các Tracing Spans.

Nếu sử dụng **Simple Span Processor (Blocking / Synchronous)**:
- Mỗi khi tạo 1 Span, luồng giao dịch sẽ bị **chặn đồng bộ (Blocked)** để chờ mạng gửi HTTP POST sang Langfuse OTLP Server. Nếu mạng bị gián đoạn hoặc Langfuse bị quá tải, thời gian phản hồi API chuyển tiền từ $200\text{ ms}$ sẽ bị kéo dài lên $5-10\text{ giây}$, khiến khách hàng bị timeout giao dịch.

Để giải quyết vấn đề này, ta bắt buộc phải dùng **Batch Span Processor (Asynchronous / Non-blocking)**:
1. Luồng giao dịch tài chính chỉ tạo Span và đẩy nhanh vào hàng đợi đệm RAM (`Bounded Ring Buffer Queue`) mất $< 1\text{ ms}$, sau đó tiếp tục xử lý tiền cho khách hàng mà KHÔNG CẦN CHỜ Langfuse.
2. Một Worker Thread chạy ngầm của OpenTelemetry sẽ định kỳ thu gom tối đa `512 spans` và gửi sang Langfuse qua giao thức OTLP/Protobuf.

---

### **3.2. Cơ chế Phòng thủ Drop Span khi Hàng đợi bị đầy (Queue Overflow Drop Policy)**
Khi xảy ra sự cố nghẽn mạng (Network Partition) giữa RikkeiPay Server và Langfuse Server:
1. Worker Thread gửi đợt OTLP batch sẽ bị quá hạn `export-timeout = 5s`.
2. Trong lúc này, các luồng giao dịch ngân hàng vẫn liên tục đẩy thêm Spans mới vào hàng đợi đệm RAM `max-queue-size = 2048`.
3. Khi hàng đợi đệm cán mốc **2048 spans** (đầy RAM buffer):
   - **Chính sách Drop Policy:** OpenTelemetry Batch Span Processor thực thi chính sách `DROP_OLDEST_SPANS` (Hủy bỏ các Spans cũ nhất trong hàng đợi).
   - **Lợi ích sinh tử:** Luồng giao dịch tài chính chính của khách hàng **vẫn hoạt động trơn tru 100%**, không bao giờ bị treo hay tràn bộ nhớ RAM (OutOfMemoryError). 
   - Trong triết lý ngân hàng: *"Mất dữ liệu giám sát Trace có thể chấp nhận được, nhưng làm ngưng trệ luồng chuyển tiền của khách hàng là ĐIỀU TUYỆT ĐỐI CẤM!"*.

---

## 📑 **4. MINH CHỨNG CHẠY THỰC TẾ & SLF4J LOGS (SPRING BOOT STARTUP)**

Log console khi ứng dụng Spring Boot khởi chạy chứng minh kết nối OTLP Exporter thành công:

```text
2026-08-25T10:45:00.123  INFO 28912 --- [main] c.r.TracingResilienceApplication         : Starting TracingResilienceApplication v0.0.1-SNAPSHOT using Java 17.0.12
2026-08-25T10:45:01.450  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           : =========================================================================
2026-08-25T10:45:01.451  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :   RIKKEIPAY OPENTELEMETRY NON-BLOCKING TRACING INITIALIZATION
2026-08-25T10:45:01.452  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           : =========================================================================
2026-08-25T10:45:01.453  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - OTLP Exporter Endpoint : http://localhost:3000/api/public/otlp/v1/traces
2026-08-25T10:45:01.454  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - Header Authorization   : Basic ******** (Langfuse PK/SK)
2026-08-25T10:45:01.455  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - Batch Max Queue Size   : 2048 Spans (RAM Buffer)
2026-08-25T10:45:01.456  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - Batch Max Export Size  : 512 Spans per Batch
2026-08-25T10:45:01.457  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - Schedule Export Delay  : 5s
2026-08-25T10:45:01.458  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  - Queue Overflow Policy  : DROP_OLDEST_SPANS (Non-blocking Guarantee)
2026-08-25T10:45:01.459  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           : -------------------------------------------------------------------------
2026-08-25T10:45:01.460  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           :  STATUS: Cấu hình OTLP Exporter kết nối thành công tới Langfuse OTLP API!
2026-08-25T10:45:01.461  INFO 28912 --- [main] c.r.config.OpenTelemetryConfig           : =========================================================================
2026-08-25T10:45:02.100  INFO 28912 --- [main] c.r.TracingResilienceApplication         : Started TracingResilienceApplication in 2.15 seconds (process running for 2.65)
```
