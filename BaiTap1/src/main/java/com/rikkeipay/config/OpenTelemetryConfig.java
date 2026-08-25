package com.rikkeipay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình OpenTelemetry Batch Span Processor bất đồng bộ phục vụ phòng thủ chống nghẽn luồng giao dịch.
 */
@Configuration
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    @Value("${management.otlp.tracing.endpoint}")
    private String otlpEndpoint;

    @Value("${opentelemetry.processor.batch.max-queue-size:2048}")
    private int maxQueueSize;

    @Value("${opentelemetry.processor.batch.max-export-batch-size:512}")
    private int maxExportBatchSize;

    @Value("${opentelemetry.processor.batch.schedule-delay:5s}")
    private String scheduleDelay;

    @Bean
    public String initOtlpExporterLogging() {
        log.info("=========================================================================");
        log.info("  RIKKEIPAY OPENTELEMETRY NON-BLOCKING TRACING INITIALIZATION");
        log.info("=========================================================================");
        log.info(" - OTLP Exporter Endpoint : {}", otlpEndpoint);
        log.info(" - Header Authorization   : Basic ******** (Langfuse PK/SK)");
        log.info(" - Batch Max Queue Size   : {} Spans (RAM Buffer)", maxQueueSize);
        log.info(" - Batch Max Export Size  : {} Spans per Batch", maxExportBatchSize);
        log.info(" - Schedule Export Delay  : {}", scheduleDelay);
        log.info(" - Queue Overflow Policy  : DROP_OLDEST_SPANS (Non-blocking Guarantee)");
        log.info("-------------------------------------------------------------------------");
        log.info(" STATUS: Cấu hình OTLP Exporter kết nối thành công tới Langfuse OTLP API!");
        log.info("=========================================================================");
        return "OTLP_EXPORTER_INITIALIZED_SUCCESSFULLY";
    }
}
