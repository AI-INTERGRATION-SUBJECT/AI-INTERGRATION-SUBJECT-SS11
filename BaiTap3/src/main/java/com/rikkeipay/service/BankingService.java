package com.rikkeipay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BankingService {

    private static final Logger log = LoggerFactory.getLogger(BankingService.class);

    public String processTransfer(String senderAcc, String receiverAcc, BigDecimal amount) {
        log.info("[SERVICE LOG 1] Đang xác thực số dư tài khoản nguồn: {}", senderAcc);
        log.info("[SERVICE LOG 2] Đang gọi Core Banking chuyển {} VND sang STK: {}", amount, receiverAcc);

        // Giả lập xử lý thành công
        String transactionId = "TXN-" + System.currentTimeMillis();
        log.info("[SERVICE LOG 3] Giao dịch thành công! Mã GD: {}", transactionId);

        return transactionId;
    }
}
