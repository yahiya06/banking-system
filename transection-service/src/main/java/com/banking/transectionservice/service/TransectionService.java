package com.banking.transectionservice.service;

import com.banking.transectionservice.dto.TransectionRequest;
import com.banking.transectionservice.dto.TransectionResponse;
import com.banking.transectionservice.repository.TransectionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransectionService {
    private final TransectionRepository transectionRepository;

    private static final String TRANSECTION_INITIATED_TOPIC = "transection.initiated";
    private static final String TRANSECTION_COMPLETED_TOPIC = "transection.completed";
    private static final String TRANSECTION_REFUNDED_TOPIC = "transection.refunded";


    public TransectionResponse transfer(@Valid TransectionRequest request) {
        log.info("SAGA START- transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount()
        );


    }
}
