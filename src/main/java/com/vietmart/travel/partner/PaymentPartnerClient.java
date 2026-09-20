package com.vietmart.travel.partner;

import com.vietmart.travel.enums.TestScenario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class PaymentPartnerClient {

    public String processPayment(String customerId, BigDecimal amount, String paymentMethod, TestScenario scenario) {
        log.info("[PAYMENT GATEWAY] Processing payment of {} VND for customer {} via {}", amount, customerId, paymentMethod);

        if (scenario == TestScenario.PAYMENT_FAILED) {
            log.error("[PAYMENT GATEWAY ERROR] Payment declined: Insufficient funds or card authorization failed!");
            throw new IllegalArgumentException("Thẻ không đủ số dư hoặc giao dịch bị từ chối bởi Ngân hàng");
        }

        String transactionId = "PAY-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[PAYMENT GATEWAY SUCCESS] Payment completed. Transaction ID: {}", transactionId);
        return transactionId;
    }

    public boolean refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.warn("[PAYMENT GATEWAY COMPENSATE] Refunding transaction {} amount {} VND due to: {}", transactionId, amount, reason);
        log.info("[PAYMENT GATEWAY COMPENSATE SUCCESS] Refund of {} VND completed for transaction {}.", amount, transactionId);
        return true;
    }
}
