package com.banking.transectionservice.service;

import com.banking.transectionservice.entity.Transection;
import com.banking.transectionservice.entity.TransectionStatus;
import com.banking.transectionservice.repository.TransectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransectionEventConsumer {

    private final TransectionRepository transectionRepository;
    private final RedisTemplate<String,String> redisTemplate;
    private static final long OTP_EXPIRY_MINUTES = 5;

    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String TRANSECTION_OTP_GENERATED_TOPIC = "transection.otp.generated";

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(@Payload Map<String,Object> payload){
        try{
            String transectionId = (String) payload.get("transectionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required - transection: {} reason: {} ",transectionId,reason);

            Transection transection = transectionRepository.findById(transectionId)
                    .orElseThrow(() -> new RuntimeException("Transection not found: "+ transectionId));

            if (transection.getStatus() != TransectionStatus.PROCESSING){
                log.warn("Transection: {} not processing - skipping",transectionId);
                return;
            }

            //Generate OTP
            String otp = String.format("%06d", (int) (Math.random() * 900000) + 100000);

            //Store OTP in redis expire in 5 min
            String otpKey = "transection:otp" +transectionId;
            redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

            //update status
            transection.setStatus(TransectionStatus.PENDING_VERIFICATION);
            transectionRepository.save(transection);

            log.info("OTP generated for transection: {} otp expire in {} min",transectionId,OTP_EXPIRY_MINUTES);

            //Notify user
            Map<String,Object> otpEvent = new HashMap<>();
            otpEvent.put("transectionId",transectionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount",payload.get("amount"));

            kafkaTemplate.send(TRANSECTION_OTP_GENERATED_TOPIC,transectionId,otpEvent);

        } catch (Exception e) {
            log.error("Error handling verification required: {}",e.getMessage());
        }

    }


}
