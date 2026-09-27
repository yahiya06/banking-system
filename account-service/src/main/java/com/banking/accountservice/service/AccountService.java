package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@AllArgsConstructor
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final SecureRandom random;

    public AccountResponse createAccount(CreateAccountRequest request){
        log.info("Create account for: {}",request.getEmail());

        if (accountRepository.existsByemail(request.getEmail())){
            throw new RuntimeException("Account already exists for this email:"+request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVING
                ? new BigDecimal("100000") : new BigDecimal("500000")
        );

        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}" , savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);
    }

    /**
     * get account by number
     * @param accountNumber
     * @return
     */
    public AccountResponse getAccount(String accountNumber){
        Account account = accountRepository.getByAccountNumber(accountNumber).orElseThrow(
                () ->new RuntimeException("Account not found")
        );
        return mapToResponse(account);
    }

    /**
     * get account balance
     * @param accountNumber
     * @return
     */
    public BigDecimal getBalance(String accountNumber){
        Account account = accountRepository.getByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("Account not found")
        );

        return account.getBalance();
    }

    /**
     * Block account - Called By fraud detection service by kafka
     * @param accountNumber
     */
    public void blockAccount(String accountNumber){
        log.info("Blocking account: {}",accountNumber );
        Account account = accountRepository.getByAccountNumber(accountNumber).orElseThrow(
                ()-> new RuntimeException("Account not found")
        );
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}",accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount){
        log.info("Deducting balance {} from account {}", amount ,accountNumber);
        Account account = accountRepository.getByAccountNumber(accountNumber).orElseThrow(
                ()-> new RuntimeException("Account not found")
        );

        if (account.getAccountStatus()!= AccountStatus.ACTIVE){
            throw new RuntimeException("Account not active:"+ accountNumber);
        }

        if (account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient fund for account:"+ accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance Updated, new balance is {}", account.getBalance());
    }

    /**
     * Credit method called by transection service by kafka
     * @return
     */
    public void creditBalance(String accountNumber, BigDecimal amount){
        Account account = accountRepository.getByAccountNumber(accountNumber).orElseThrow(
                ()-> new RuntimeException("Account not found ")
        );

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance credited. new balance {}", account.getBalance());
    }

    private String generateAccountNumber(){
        String accountNumber;

        long number = random.nextLong(1_000_000_000_00L);
        do{
            accountNumber = String.format("%012d", number);
        }while(accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private AccountResponse  mapToResponse(Account account){
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setAccountStatus(account.getAccountStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());

        return response;
    }
}
