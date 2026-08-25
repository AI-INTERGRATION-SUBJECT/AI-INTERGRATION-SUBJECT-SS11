package com.rikkeipay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility Class tính toán chi phí tiêu thụ Token LLM sử dụng BigDecimal
 * đảm bảo độ chính xác tuyệt đối theo chuẩn tài chính ngân hàng.
 */
public class LlmCostCalculator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    // Bảng giá gemini-2.5-flash per 1,000,000 tokens
    private static final BigDecimal GEMINI_FLASH_INPUT_RATE = new BigDecimal("0.075");
    private static final BigDecimal GEMINI_FLASH_OUTPUT_RATE = new BigDecimal("0.300");

    // Bảng giá deepseek-v3 per 1,000,000 tokens
    private static final BigDecimal DEEPSEEK_V3_INPUT_RATE = new BigDecimal("0.140");
    private static final BigDecimal DEEPSEEK_V3_OUTPUT_RATE = new BigDecimal("0.280");

    /**
     * Tính toán chi phí LLM dựa trên số lượng Input Tokens, Output Tokens và Mô hình.
     *
     * @param inputTokens Số lượng token đầu vào (Prompt)
     * @param outputTokens Số lượng token đầu ra (Completion)
     * @param model Tên mô hình (Ví dụ: 'gemini-2.5-flash' hoặc 'deepseek-v3')
     * @return Chi phí tính bằng USD (kiểu BigDecimal)
     */
    public static BigDecimal calculateCost(long inputTokens, long outputTokens, String model) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Số lượng tokens không được là số âm");
        }

        BigDecimal inputRate = GEMINI_FLASH_INPUT_RATE;
        BigDecimal outputRate = GEMINI_FLASH_OUTPUT_RATE;

        if (model != null && model.toLowerCase().contains("deepseek")) {
            inputRate = DEEPSEEK_V3_INPUT_RATE;
            outputRate = DEEPSEEK_V3_OUTPUT_RATE;
        }

        // Calculation using BigDecimal to avoid IEEE 754 floating point rounding errors
        BigDecimal inputCost = new BigDecimal(inputTokens)
                .multiply(inputRate)
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);

        BigDecimal outputCost = new BigDecimal(outputTokens)
                .multiply(outputRate)
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);

        return inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Định dạng chuỗi hiển thị chi phí chuẩn dạng $0.00000000 (scale = 8) phục vụ ghi log kiểm toán.
     *
     * @param cost Giá trị chi phí BigDecimal
     * @return Chuỗi định dạng tiền tệ USD (Ví dụ: '$0.00148500')
     */
    public static String formatCost(BigDecimal cost) {
        if (cost == null) {
            return "$0.00000000";
        }
        BigDecimal scaledCost = cost.setScale(8, RoundingMode.HALF_UP);
        return "$" + scaledCost.toPlainString();
    }
}
