package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "transection.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String,Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String transectionId = (String) payload.get("transectionId");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert(
                    accountNumber,
                    "TRANSECTION VERIFICATION REQUIRED",
                    String.format(
                            "Suspicious activity detected on your account." +
                             "Reason %s" +
                              "A transection of %s is pending verification. " +
                              "Your otp is %s. Valid for 5 minutes. " +
                              "If this wasn't you - ignore this message."
                    )
            );
        } catch (Exception e) {
            log.error("Error sending OTP notification: {}", e.getMessage());

        }
    }

    private void sendAlert(String accountNumber, String subject, String message) {

    }


}
