package com.rikkeipay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Bean LangfuseClient nạp các thuộc tính từ application.yml.
 */
@Configuration
public class LangfuseConfig {

    private static final Logger log = LoggerFactory.getLogger(LangfuseConfig.class);

    private final LangfuseProperties properties;

    public LangfuseConfig(LangfuseProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LangfuseClient langfuseClient() {
        log.info("[LANGFUSE CONFIG] Khởi tạo LangfuseClient kết nối tới Server: {}", properties.getBaseUrl());
        return new LangfuseClient(properties.getPublicKey(), properties.getSecretKey(), properties.getBaseUrl());
    }

    /**
     * Stub class giả lập LangfuseClient SDK.
     */
    public static class LangfuseClient {
        private final String publicKey;
        private final String secretKey;
        private final String baseUrl;
        private boolean isOnline = true; // Cờ kiểm soát mô phỏng Server Online / Offline

        public LangfuseClient(String publicKey, String secretKey, String baseUrl) {
            this.publicKey = publicKey;
            this.secretKey = secretKey;
            this.baseUrl = baseUrl;
        }

        public void setOnline(boolean online) {
            this.isOnline = online;
        }

        public boolean isOnline() {
            return isOnline;
        }

        public String getPromptFromRegistry(String promptName, String label) throws RuntimeException {
            if (!isOnline || baseUrl == null || baseUrl.contains("offline")) {
                throw new RuntimeException("Langfuse Server Timeout / Connection Refused (Server Offline)");
            }
            return """
                [LANGFUSE REGISTRY PROMPT - PRODUCTION VERSION 2.1]
                Bạn là Giao dịch viên Ngân hàng số RikkeiPay Assistant chuyên nghiệp.
                Thông tin tài khoản:
                - Chủ tài khoản: {{user_name}}
                - Số dư khả dụng: {{current_balance}} VND
                - Chính sách ngân hàng: {{bank_policy}}
                
                Yêu cầu chuyển khoản: {{user_input}}
                Vui lòng xử lý an toàn và trả về kết quả JSON.
                """;
        }
    }
}
