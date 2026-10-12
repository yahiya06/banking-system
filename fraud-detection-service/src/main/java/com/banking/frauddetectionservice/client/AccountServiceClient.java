package com.banking.frauddetectionservice.client;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;


public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountNumber}/balance")
    public BigDecimal getBalance(@PathVariable String accountNumber);
}
