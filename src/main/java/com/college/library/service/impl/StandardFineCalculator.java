package com.college.library.service.impl;

import com.college.library.service.FineCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Implements standard fine calculation rule specified in Step 10:
 * Fine = late days * ₹5
 */
@Service
public class StandardFineCalculator implements FineCalculator {

    private final double dailyFineRate;

    public StandardFineCalculator(@Value("${library.fine.daily-rate:5.0}") double dailyFineRate) {
        if (dailyFineRate < 0) {
            throw new IllegalArgumentException("The daily fine rate cannot be negative.");
        }
        this.dailyFineRate = dailyFineRate;
    }

    @Override
    public long calculateLateDays(LocalDate dueDate, LocalDate returnDate) {
        if (returnDate == null || dueDate == null || !returnDate.isAfter(dueDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, returnDate);
    }

    @Override
    public double calculateFine(LocalDate dueDate, LocalDate returnDate) {
        long lateDays = calculateLateDays(dueDate, returnDate);
        return lateDays * dailyFineRate;
    }

    @Override
    public double getDailyRate() {
        return dailyFineRate;
    }
}
