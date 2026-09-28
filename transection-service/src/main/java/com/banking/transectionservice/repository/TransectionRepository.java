package com.banking.transectionservice.repository;

import com.banking.transectionservice.entity.Transection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransectionRepository extends JpaRepository<Transection, String> {
}
