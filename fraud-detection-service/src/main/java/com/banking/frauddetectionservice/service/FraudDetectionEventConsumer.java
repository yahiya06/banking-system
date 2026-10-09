package com.banking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEventConsumer {

    private FraudDetectionService fraudDetectionService;

    @KafkaListener(topics = "transection.initiated", groupId = "fraud-detection-group")
    public void consumeTransectionInitiated(@Payload Map<String, Object> payload){
        log.info("Received transection for fraud check {}", payload.get("transectionId"));

        try {
            fraudDetectionService.checkTransection(payload);
        }catch (Exception e){

        }
    }
}
