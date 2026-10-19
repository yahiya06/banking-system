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

    @KafkaListener(topics = "transection.completed")
    public void consumeTransectionCompleted(@Payload Map<String,Object> payload){
        try {
            String senderAccount = (String) payload.get("senderAccountNumber");
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            String amount = payload.get("amount").toString();

            //Debit alert
            sendAlert(senderAccount,"DEBIT ALERT", String.format("%s debited from account %s",amount,senderAccount));

            //Credit alert
            sendAlert(receiverAccount,"CREDIT ALERT", String.format("%s credited from account %s",amount,receiverAccount));
        } catch (Exception e) {
            log.error("Error sending transection notification: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.deteted")
    public void consumeFraudDetected(@Payload Map<String,Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            sendAlert(accountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                     String.format(
                             "Your account %s is blocked. " +
                             "Reason: %s. " +
                             "Please contact your bank immediately.",
                             accountNumber,reason
                     ));
        } catch (Exception e) {
            log.error("Error sending fraud alert: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "transection.refunded")
    public void consumeTransectionRefunded(@Payload Map<String,Object> payload){
        try {
            String senderAccount = (String) payload.get("senderAccountNumber");
            String reason = (String) payload.get("reason");
            String amount = payload.get("amount").toString();

            sendAlert(senderAccount,
                    "REFUND PROCESSED",
                    String.format(
                            "Your transection of %s was cancelled. " +
                            "Reason %s" +
                            "%s has been refunded to account %s.",
                            amount,reason,amount,senderAccount
                    ));
        } catch (Exception e) {
            log.error("Error sending refund notification: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String,Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();

            sendAlert(accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format(
                            "Payment of %s is completed" +
                            "Razorpay Id: %s",
                            amount,payload.get("razorpayPaymentId")
                    ));
        } catch (Exception e) {
            log.error("Error sending payment notification: {}", e.getMessage());
        }
    }

    public void consumePaymentFailed(@Payload Map<String,Object> payload){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();

            sendAlert(accountNumber,
                    "PAYMENT FAILED",
                    String.format(
                            "Your payment of %s could not be processed" +
                            "Please try again or contact support.",
                            amount
                    )
                    );
        } catch (Exception e) {
            log.error("Error sending payment failure notification: {}", e.getMessage());
        }

    }

    private void sendAlert(String accountNumber, String subject, String message) {
        log.info("------------------------------------------");
        log.info("Account: {}",accountNumber);
        log.info("Subject: {}",subject);
        log.info("Message: {}",message);
        log.info("------------------------------------------");
    }


}
