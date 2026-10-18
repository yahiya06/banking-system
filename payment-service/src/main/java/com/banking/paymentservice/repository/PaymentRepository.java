package com.banking.paymentservice.repository;

import com.banking.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment,String> {
}
