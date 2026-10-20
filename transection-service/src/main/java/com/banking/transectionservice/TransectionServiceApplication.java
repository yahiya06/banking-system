package com.banking.transectionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TransectionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransectionServiceApplication.class, args);
	}

}
