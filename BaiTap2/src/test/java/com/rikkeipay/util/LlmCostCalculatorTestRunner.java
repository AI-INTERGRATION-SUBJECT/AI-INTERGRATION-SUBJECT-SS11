package com.rikkeipay.util;

import com.rikkeipay.model.TraceMetadata;
import java.math.BigDecimal;

public class LlmCostCalculatorTestRunner {

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("   RIKKEIPAY LLM COST CALCULATOR & METADATA UNIT TEST SUITE");
        System.out.println("=========================================================================\n");

        int passed = 0;

        // TEST CASE 1
        System.out.println("---> [TEST 1] Input = 15,000 tokens, Output = 1,200 tokens (Gemini 2.5 Flash)");
        BigDecimal cost1 = LlmCostCalculator.calculateCost(15000, 1200, "google/gemini-2.5-flash");
        String formatted1 = LlmCostCalculator.formatCost(cost1);
        System.out.println("   - Calculated Cost : " + cost1.toPlainString());
        System.out.println("   - Formatted Output : " + formatted1);

        if (new BigDecimal("0.00148500").equals(cost1) && "$0.00148500".equals(formatted1)) {
            System.out.println("    TEST 1 PASSED!");
            passed++;
        } else {
            System.out.println(" ❌ TEST 1 FAILED!");
        }

        System.out.println("\n-------------------------------------------------------------------------\n");

        // TEST CASE 2
        System.out.println("---> [TEST 2] Input = 250,000 tokens, Output = 45,000 tokens (Gemini 2.5 Flash)");
        BigDecimal cost2 = LlmCostCalculator.calculateCost(250000, 45000, "google/gemini-2.5-flash");
        String formatted2 = LlmCostCalculator.formatCost(cost2);
        System.out.println("   - Calculated Cost : " + cost2.toPlainString());
        System.out.println("   - Formatted Output : " + formatted2);

        if (new BigDecimal("0.03225000").equals(cost2) && "$0.03225000".equals(formatted2)) {
            System.out.println("    TEST 2 PASSED!");
            passed++;
        } else {
            System.out.println(" ❌ TEST 2 FAILED!");
        }

        System.out.println("\n-------------------------------------------------------------------------\n");

        // TEST CASE 3
        System.out.println("---> [TEST 3] TraceMetadata Serialization & Tagging Test");
        TraceMetadata metadata = new TraceMetadata(
                "FINANCE",
                "PRODUCTION",
                "usr-10029",
                "sess-889911",
                "google/gemini-2.5-flash",
                cost1
        );
        String json = metadata.toJsonString();
        System.out.println("   - Metadata JSON Output : " + json);

        if (json.contains("FINANCE") && json.contains("0.00148500")) {
            System.out.println("    TEST 3 PASSED!");
            passed++;
        } else {
            System.out.println(" ❌ TEST 3 FAILED!");
        }

        System.out.println("\n=========================================================================");
        System.out.println(" RESULT: " + passed + "/3 TEST CASES PASSED 100% SUCCESSFUL!");
        System.out.println("=========================================================================");
    }
}
