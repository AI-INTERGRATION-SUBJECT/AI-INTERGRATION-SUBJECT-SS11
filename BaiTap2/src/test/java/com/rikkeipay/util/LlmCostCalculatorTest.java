package com.rikkeipay.util;

import com.rikkeipay.model.TraceMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LlmCostCalculatorTest {

    @Test
    @DisplayName("Test Case 1: Input = 15,000 tokens, Output = 1,200 tokens (Gemini 2.5 Flash)")
    void testCalculateCost_TestCase1() {
        long inputTokens = 15000;
        long outputTokens = 1200;
        String model = "google/gemini-2.5-flash";

        BigDecimal calculatedCost = LlmCostCalculator.calculateCost(inputTokens, outputTokens, model);
        String formattedCost = LlmCostCalculator.formatCost(calculatedCost);

        System.out.println("=========================================================================");
        System.out.println(" [TEST CASE 1 RESULTS]");
        System.out.println("  - Input Tokens  : " + inputTokens);
        System.out.println("  - Output Tokens : " + outputTokens);
        System.out.println("  - Calculated Cost: " + calculatedCost.toPlainString());
        System.out.println("  - Formatted String: " + formattedCost);
        System.out.println("=========================================================================");

        assertEquals(new BigDecimal("0.00148500"), calculatedCost);
        assertEquals("$0.00148500", formattedCost);
    }

    @Test
    @DisplayName("Test Case 2: Input = 250,000 tokens, Output = 45,000 tokens (Gemini 2.5 Flash)")
    void testCalculateCost_TestCase2() {
        long inputTokens = 250000;
        long outputTokens = 45000;
        String model = "google/gemini-2.5-flash";

        BigDecimal calculatedCost = LlmCostCalculator.calculateCost(inputTokens, outputTokens, model);
        String formattedCost = LlmCostCalculator.formatCost(calculatedCost);

        System.out.println("=========================================================================");
        System.out.println(" [TEST CASE 2 RESULTS]");
        System.out.println("  - Input Tokens  : " + inputTokens);
        System.out.println("  - Output Tokens : " + outputTokens);
        System.out.println("  - Calculated Cost: " + calculatedCost.toPlainString());
        System.out.println("  - Formatted String: " + formattedCost);
        System.out.println("=========================================================================");

        assertEquals(new BigDecimal("0.03225000"), calculatedCost);
        assertEquals("$0.03225000", formattedCost);
    }

    @Test
    @DisplayName("Test Case 3: Chuyển đổi TraceMetadata sang Map và JSON String")
    void testTraceMetadataConversion() {
        BigDecimal cost = LlmCostCalculator.calculateCost(15000, 1200, "gemini-2.5-flash");
        TraceMetadata metadata = new TraceMetadata(
                "FINANCE",
                "PRODUCTION",
                "usr-10029",
                "sess-889911",
                "google/gemini-2.5-flash",
                cost
        );

        assertNotNull(metadata.toMap());
        assertEquals("FINANCE", metadata.toMap().get("department"));
        assertEquals("PRODUCTION", metadata.toMap().get("environment"));
        assertEquals("0.00148500", metadata.toMap().get("estimatedCostUsd"));

        String json = metadata.toJsonString();
        assertTrue(json.contains("FINANCE"));
        assertTrue(json.contains("0.00148500"));

        System.out.println(" [TEST CASE 3 RESULTS - TRACE METADATA JSON]");
        System.out.println("  " + json);
    }
}
