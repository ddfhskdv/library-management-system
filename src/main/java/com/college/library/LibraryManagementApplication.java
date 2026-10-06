package com.college.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application Entry Point.
 * Advanced Programming Practice Project - Library Management System
 */
@SpringBootApplication
@EnableScheduling
public class LibraryManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryManagementApplication.class, args);
        System.out.println("=========================================================");
        System.out.println("  Library Management System is running successfully!");
        System.out.println("  Open your browser at: http://localhost:8081");
        System.out.println("  H2 Database Console:   http://localhost:8081/h2-console");
        System.out.println("=========================================================");
    }
}
