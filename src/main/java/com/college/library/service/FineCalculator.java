package com.college.library.service;

import java.time.LocalDate;

/**
 * Interface demonstrating OOP Abstraction and Polymorphism.
 * Allows pluggable fine calculation strategies (e.g., standard ₹5/day, holiday discounts, etc.).
 */
public interface FineCalculator {

    /**
     * Calculates total late days given a due date and return date.
     */
    long calculateLateDays(LocalDate dueDate, LocalDate returnDate);

    /**
     * Calculates the monetary fine based on overdue days.
     */
    double calculateFine(LocalDate dueDate, LocalDate returnDate);

    /**
     * Returns the fine rate per day.
     */
    double getDailyRate();
}
