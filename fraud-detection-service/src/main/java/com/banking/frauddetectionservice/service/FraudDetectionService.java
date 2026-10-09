package com.banking.frauddetectionservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class FraudDetectionService {

    public void checkTransection(Map<String, Object> payload) {
        String transectionId = (String) payload.get("transectionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
    }
}
