package com.example.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinanceApplication {
    public static void main(String[] args) {
        // 这行代码是启动 Spring Boot 的核心，它会自动扫描该包及其子包下的所有 Controller
        SpringApplication.run(FinanceApplication.class, args);
    }
}