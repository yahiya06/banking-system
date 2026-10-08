package com.banking.transectionservice.repository;

import com.banking.transectionservice.entity.Transection;
import jdk.dynalink.linker.LinkerServices;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransectionRepository extends JpaRepository<Transection, String> {
    List<Transection> findBySenderAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
