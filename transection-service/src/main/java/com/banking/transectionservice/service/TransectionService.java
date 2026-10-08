package com.banking.transectionservice.service;

import com.banking.transectionservice.client.AccountServiceClient;
import com.banking.transectionservice.dto.TransectionRequest;
import com.banking.transectionservice.dto.TransectionResponse;
import com.banking.transectionservice.entity.Transection;
import com.banking.transectionservice.entity.TransectionStatus;
import com.banking.transectionservice.entity.TransectionType;
import com.banking.transectionservice.event.TransectionInitiatedEvent;
import com.banking.transectionservice.repository.TransectionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransectionService {
    private final TransectionRepository transectionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String TRANSECTION_INITIATED_TOPIC = "transection.initiated";
    private static final String TRANSECTION_COMPLETED_TOPIC = "transection.completed";
    private static final String TRANSECTION_REFUNDED_TOPIC = "transection.refunded";


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



}
