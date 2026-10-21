package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String > redisTemplate;

    @Value("${fraud.max-transection-per-minute}")
    private int maxTransectionPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    private static final String VERIFICATION_REQUIRED_TOPIC="verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC="fraud.check.clean";

    public void checkTransection(Map<String, Object> payload) {
        String transectionId = (String) payload.get("transectionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("checking transection: {} account: {} amount: {} balance: {}", transectionId, accountNumber,amount, senderBalance);

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
            transectionCleanEvent.put("isFraud", false);
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
        if (isAmountSuspicious(accountNumber,amount)){
            return new FraudCheckResult(true,"Unusual transection amount exceeded 3x your average");
        }

        //pattern 3 : balance check
        if (senderBalance.compareTo(BigDecimal.ZERO) >0 && isBalanceCheckFailed(senderBalance,amount)){
            return new FraudCheckResult(true,"Transection exceed 90% of your account balance");
        }

        return new FraudCheckResult(false,null);
    }

    private boolean isVelocityExceeded(String accountNumber){
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count ==1 ){
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }

        log.info("Velocity check- account: {} count: {}/{}",accountNumber,count,maxTransectionPerMinute);

        return count != null && count > maxTransectionPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber,BigDecimal amount){
        String avgKey = "fraud:avg_amount" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if (avgStr == null){
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return false;
        }
        BigDecimal averageAmount = new BigDecimal(avgStr);
        BigDecimal threshold = averageAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        BigDecimal newAvg = averageAmount.add(amount).divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);

        redisTemplate.opsForValue().set(avgKey,newAvg.toString());

        log.info("Amount check - amount: {} threshold: {} suspicious: {}",amount,threshold,amount.compareTo(threshold)>0);

        return amount.compareTo(threshold)>0;
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount){
        BigDecimal maxAllowed = senderBalance.multiply(BigDecimal.valueOf(maxBalancePercentage));

        log.info("Balance check - amount: {} maxAllowed: {} suspisious: {}",amount,maxAllowed,amount.compareTo(maxAllowed)>0);

        return amount.compareTo(maxAllowed)>0;
    }
}
