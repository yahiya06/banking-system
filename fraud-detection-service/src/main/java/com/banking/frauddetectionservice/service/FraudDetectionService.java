package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String VERIFICATION_REQUIRED_TOPIC="verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC="verification.required";

    public void checkTransection(Map<String, Object> payload) {
        String transectionId = (String) payload.get("transectionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());


        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("checking taransection: {} account: {} amount: {} balance: {}", transectionId, accountNumber,amount, senderBalance);

        FraudCheckResult result = performFraudCheck(accountNumber,amount,senderBalance);

        if (result.isFraud()){
            log.info("Suspicious activity detected- account; {} reason: {}- requesting otp verification",accountNumber,result.getReason());

            Map<String ,Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transectionId",transectionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transectionId,verificationEvent);
        }else {
            log.info("Transection clean");

            Map<String,Object> transectionCleanEvent = new HashMap<>();
            transectionCleanEvent.put("transectionId",transectionId);
            transectionCleanEvent.put("isfraud", false);
            transectionCleanEvent.put("reason",null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transectionId,transectionCleanEvent);
        }
    }

    private FraudCheckResult performFraudCheck(String accountNumber, BigDecimal amount, BigDecimal senderBalance){

        //pattern 1 : velocity check
        if (isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(true,"Too many transection in 60 second velocity exceeded");
        }

        //pattern : amount check
        if (isAmounySuspicious(accountNumber,amount)){
            return new FraudCheckResult(true,"Unusual transection amount exceeded 3x your average");
        }

        //pattern 3 : balance check
        if (senderBalance.compareTo(BigDecimal.ZERO) >0 && isBalanceCheckFailed(accountNumber,amount)){
            return new FraudCheckResult(true,"Transection exceed 90% of your account balance");
        }

        return new FraudCheckResult(false,null);
    }

    private boolean isVelocityExceeded(String accountNumber){

    }
}
