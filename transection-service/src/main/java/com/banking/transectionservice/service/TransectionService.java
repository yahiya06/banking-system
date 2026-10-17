package com.banking.transectionservice.service;

import com.banking.transectionservice.client.AccountServiceClient;
import com.banking.transectionservice.dto.TransectionRequest;
import com.banking.transectionservice.dto.TransectionResponse;
import com.banking.transectionservice.entity.Transection;
import com.banking.transectionservice.entity.TransectionStatus;
import com.banking.transectionservice.entity.TransectionType;
import com.banking.transectionservice.event.TransectionCompletedEvent;
import com.banking.transectionservice.event.TransectionInitiatedEvent;
import com.banking.transectionservice.repository.TransectionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransectionService {
    private final TransectionRepository transectionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    private static final String TRANSECTION_INITIATED_TOPIC = "transection.initiated";
    private static final String TRANSECTION_COMPLETED_TOPIC = "transection.completed";
    private static final String TRANSECTION_REFUNDED_TOPIC = "transection.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.deteted";


    public TransectionResponse transfer(@Valid TransectionRequest request) {
        log.info("SAGA START- transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount()
        );

        accountServiceClient.deductBalance(
                request.getReceiverAccountNumber(),
                request.getAmount());

        Transection transection = new Transection();

        transection.setSenderAccountNumber(request.getSenderAccountNumber());
        transection.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transection.setAmount(request.getAmount());
        transection.setType(TransectionType.TRANSFER);
        transection.setStatus(TransectionStatus.PROCESSING);
        transection.setDescription(request.getDescription());
        transection.setReferenceNumber(UUID.randomUUID().toString());

        Transection savedTransection = transectionRepository.save(transection);
        log.info("Transection saved as processing: {}", savedTransection.getId());

        TransectionInitiatedEvent event = new TransectionInitiatedEvent(
                savedTransection.getId(),
                savedTransection.getSenderAccountNumber(),
                savedTransection.getReceiverAccountNumber(),
                savedTransection.getAmount(),
                savedTransection.getDescription()
        );

        kafkaTemplate.send(TRANSECTION_INITIATED_TOPIC, savedTransection.getId(), event);
        log.info("TransectionInitiatedEvent published: {}", savedTransection.getId());

        return mapToResponse(savedTransection);
    }

    public TransectionResponse getTransection(String transectionId) {
        return mapToResponse(transectionRepository
                .findById(transectionId)
                .orElseThrow(()-> new RuntimeException("Transection not found"+transectionId))
        );
    }

    public List<TransectionResponse> getTransectionHistory(String accountNumber) {

        return transectionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransectionResponse verifyOTP(String transectionId, String otp){
        log.info("OTP verification for the transection {}",transectionId);

        Transection transection = transectionRepository.findById(transectionId)
                .orElseThrow(()-> new RuntimeException("Transection not found"+transectionId));

        String otpKey = "transection:otp" +transectionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        //OTP expired
        if (storedOtp == null){
            log.warn("OTP expired for transection: {}", transectionId);
            compensateTransection(transection,"OTP expired - transection cancelled and amount refunded");
            return mapToResponse(transection);
        }

        //Wrong otp Blocking account and refund
        if (!storedOtp.equals(otp)){
            log.warn("Wrong OTP - blocking account and refunding {}",transectionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transection,"Wrong OTP entered - transection cancelled, " +
                    "account blocking for security");
            return mapToResponse(transection);
        }

        //OTP correct - complete transection
        log.info("OTP verified - completing transection: {}",transectionId);
        redisTemplate.delete(otpKey);
        completeTransection(transection);
        return mapToResponse(transection);
    }

    private void compensateTransection(Transection transection, String reason){
        log.warn("SAGA COMPENSATION - refunding: {}, amount: {}",transection.getSenderAccountNumber(),transection.getAmount());

        //CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY
        accountServiceClient.creditBalance(transection.getSenderAccountNumber(),transection.getAmount());
        transection.setStatus(TransectionStatus.FLAGGED);
        transection.setFailureReason(reason + " - SAGA Compensation executed, amount refunded at"+ LocalDateTime.now());

        transectionRepository.save(transection);

        //PUBLISH refund event - Notification service will alert user
        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transectionId",transection.getId());
        refundEvent.put("SenderAccountNumber",transection.getSenderAccountNumber());
        refundEvent.put("amount",transection.getAmount());
        refundEvent.put("reason",reason);

        kafkaTemplate.send(TRANSECTION_REFUNDED_TOPIC,transection.getId(),refundEvent);

        log.info("SAGA COMPENSATION COMPLETED - {} refunded to {}",transection.getAmount(),transection.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transection transection, String reason){
        //publish fraud.detected -> account service will block account
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transectionId",transection.getId());
        fraudEvent.put("accountNumber", transection.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transection.getSenderAccountNumber(), fraudEvent);

        log.warn("fraud.detected published - account {} will be blocked, Kindly contact to the bank",transection.getSenderAccountNumber());

        //SAGA COMPENSATION - refund sender
        compensateTransection(transection,reason);
    }

    private void completeTransection(Transection transection){
        transection.setStatus(TransectionStatus.COMPLETED);
        transection.setCompletedAt(LocalDateTime.now());
        transectionRepository.save(transection);

        TransectionCompletedEvent completedEvent = new TransectionCompletedEvent(
                transection.getId(),
                transection.getSenderAccountNumber(),
                transection.getReceiverAccountNumber(),
                transection.getAmount(),
                transection.getDescription()
        );

        kafkaTemplate.send(TRANSECTION_COMPLETED_TOPIC,transection.getId(),completedEvent);

        log.warn("SAGA COMPLETED - transection {} completed",transection.getId());

    }

    private TransectionResponse mapToResponse(Transection transection){

        TransectionResponse response = new TransectionResponse();

        response.setId(transection.getId());
        response.setSenderAccountNumber(transection.getSenderAccountNumber());
        response.setReceiverAccountNumber(transection.getReceiverAccountNumber());
        response.setAmount(transection.getAmount());
        response.setType(transection.getType());
        response.setStatus(transection.getStatus());
        response.setDescription(transection.getDescription());
        response.setReferenceNumber(transection.getReferenceNumber());
        response.setFailureReason(transection.getFailureReason());
        response.setCreatedAt(transection.getCreatedAt());
        response.setCompletedAt(transection.getCompletedAt());

        return response;
    }

}