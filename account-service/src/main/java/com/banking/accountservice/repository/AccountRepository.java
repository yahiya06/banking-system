package com.banking.accountservice.repository;

import com.banking.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account,String> {
    boolean existsByemail(String email);
    boolean existsByAccountNumber(String accountNumber);
}
