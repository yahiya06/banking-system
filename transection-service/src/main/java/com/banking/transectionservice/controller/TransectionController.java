package com.banking.transectionservice.controller;

import com.banking.transectionservice.dto.TransectionRequest;
import com.banking.transectionservice.dto.TransectionResponse;
import com.banking.transectionservice.service.TransectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transections")
@Slf4j
@RequiredArgsConstructor
public class TransectionController {
    private final TransectionService transectionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransectionResponse> transfer(
            @Valid @RequestBody TransectionRequest request
            ){
        return ResponseEntity.status(HttpStatus.CREATED).body(transectionService.transfer(request));
    }

    @GetMapping("/{transectionId}")
    public ResponseEntity<List<TransectionResponse>> getTransection(@PathVariable String transectionId){
        return ResponseEntity.ok(transectionService.getTransection(transectionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<TransectionResponse> getTransectionHistory(@PathVariable String accountNumber){
        return ResponseEntity.ok(transectionService.getTransectionHistory(accountNumber));
    }


}
