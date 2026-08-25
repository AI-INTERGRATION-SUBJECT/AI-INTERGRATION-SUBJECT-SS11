package com.rikkeipay.controller;

import com.rikkeipay.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banking")
public class BankingController {

    private static final Logger log = LoggerFactory.getLogger(BankingController.class);

    private final BankingService bankingService;

    public BankingController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    @GetMapping("/transfer")
    public Map<String, Object> transfer(
            @RequestParam String sender,
            @RequestParam String receiver,
            @RequestParam BigDecimal amount
    ) {
        log.info("[CONTROLLER LOG] Tiếp nhận yêu cầu chuyển tiền từ client.");

        String txId = bankingService.processTransfer(sender, receiver, amount);

        log.info("[CONTROLLER LOG] Trả phản hồi thành công cho client.");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("transactionId", txId);
        return response;
    }
}
