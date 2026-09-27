package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /**
     * consume transection.completed event from kafka
     * @param payload
     */
    @KafkaListener(topics = "transection.completed")
    public void consumeTransectionCompleted(@Payload Map<String,Object> payload){
        try {
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting account {} amount {}",receiverAccount,amount);
            accountService.creditBalance(receiverAccount,amount);
        }catch (Exception e){
            log.error("Error in crediting account {}", e.getMessage());
        }
    }

    /**
     * consume fraud.detected event from kafka
     * blocked the fladge account.
     * @param payload
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String,Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            accountService.blockAccount(accountNumber);
            log.info("Fraud detected - blocking account {}",accountNumber);
        }catch (Exception e){
            log.error("Error blocking account: {}",e.getMessage());
        }
    }


}
