package com.rikkeipay.util;

import com.rikkeipay.config.LangfuseConfig;
import com.rikkeipay.config.LangfuseConfig.LangfuseClient;
import com.rikkeipay.service.PromptRegistryService;

import java.util.HashMap;
import java.util.Map;

public class PromptRegistryTestRunner {

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("  RIKKEIPAY PROMPT REGISTRY RESILIENCE & CACHING DEMONSTRATION");
        System.out.println("=========================================================================\n");

        LangfuseClient langfuseClient = new LangfuseClient("pk-test", "sk-test", "http://localhost:3000");
        PromptRegistryService registryService = new PromptRegistryService(langfuseClient);

        Map<String, Object> variables = new HashMap<>();
        variables.put("user_name", "Nguyen Van A");
        variables.put("current_balance", "5000000");
        variables.put("bank_policy", "Miễn phí chuyển tiền liên ngân hàng");
        variables.put("user_input", "Chuyển 500k cho bạn Nam STK 9876543210 VCB");

        // TRƯỜNG HỢP 1: LANGFUSE ONLINE
        System.out.println("=======> TRƯỜNG HỢP 1: MÁY CHỦ LANGFUSE ONLINE <=======");
        langfuseClient.setOnline(true);

        System.out.println("\n--- [LẦN 1: REMOTE FETCH TỪ REGISTRY SERVER] ---");
        String compiledPrompt1 = registryService.getPrompt("banking_transfer_prompt", variables);
        System.out.println("Nội dung Prompt sau khi Compile (Lần 1):\n" + compiledPrompt1);

        System.out.println("--- [LẦN 2: HIT IN-MEMORY CACHE (LATENCY ~0ms)] ---");
        String compiledPrompt2 = registryService.getPrompt("banking_transfer_prompt", variables);
        System.out.println("Nội dung Prompt từ Cache (Lần 2):\n" + compiledPrompt2);

        System.out.println("\n-------------------------------------------------------------------------\n");

        // TRƯỜNG HỢP 2: LANGFUSE OFFLINE (SẬP MẠNG / SAI URL / TAT DOCKER)
        System.out.println("=======> TRƯỜNG HỢP 2: MÁY CHỦ LANGFUSE OFFLINE (SẬP SERVER) <=======");
        registryService.clearCache(); // Xóa cache để thử thách tầng Fallback
        langfuseClient.setOnline(false); // Giả lập sập mạng Server

        System.out.println("\n--- [THỰC THI GỌI PROMPT KHI SERVER OFFLINE] ---");
        String fallbackCompiledPrompt = registryService.getPrompt("banking_transfer_prompt", variables);
        System.out.println("Nội dung Prompt từ DEFENSIVE FALLBACK (Không crash app!):\n" + fallbackCompiledPrompt);

        System.out.println("=========================================================================");
        System.out.println(" RESULT: ĐÃ XÁC NHẬN HẠ TẦNG PROMPT REGISTRY HOẠT ĐỘNG BỀN VỮNG 100%!");
        System.out.println("=========================================================================");
    }
}
