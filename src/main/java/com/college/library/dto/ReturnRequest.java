package com.college.library.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class ReturnRequest {

    @NotNull(message = "Transaction ID is required")
    private Long transactionId;

    private LocalDate returnDate; // Optional custom return date (defaults to today)

    public ReturnRequest() {
    }

    public ReturnRequest(Long transactionId) {
        this.transactionId = transactionId;
        this.returnDate = LocalDate.now();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }
}
