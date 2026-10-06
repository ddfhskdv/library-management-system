package com.college.library.dto;

/**
 * Step 11 Dashboard Metrics DTO.
 * Numbers are fetched dynamically from Java APIs rather than hardcoded.
 */
public class DashboardStats {

    private long totalBooks;
    private long availableBooks;
    private long issuedBooks;
    private long totalMembers;
    private long overdueBooks;
    private double totalFinesCollected;
    private double outstandingFines;
    private double dailyFineRate;

    public DashboardStats() {
    }

    public DashboardStats(long totalBooks, long availableBooks, long issuedBooks,
                          long totalMembers, long overdueBooks, double totalFinesCollected,
                          double outstandingFines, double dailyFineRate) {
        this.totalBooks = totalBooks;
        this.availableBooks = availableBooks;
        this.issuedBooks = issuedBooks;
        this.totalMembers = totalMembers;
        this.overdueBooks = overdueBooks;
        this.totalFinesCollected = totalFinesCollected;
        this.outstandingFines = outstandingFines;
        this.dailyFineRate = dailyFineRate;
    }

    public long getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(long totalBooks) {
        this.totalBooks = totalBooks;
    }

    public long getAvailableBooks() {
        return availableBooks;
    }

    public void setAvailableBooks(long availableBooks) {
        this.availableBooks = availableBooks;
    }

    public long getIssuedBooks() {
        return issuedBooks;
    }

    public void setIssuedBooks(long issuedBooks) {
        this.issuedBooks = issuedBooks;
    }

    public long getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(long totalMembers) {
        this.totalMembers = totalMembers;
    }

    public long getOverdueBooks() {
        return overdueBooks;
    }

    public void setOverdueBooks(long overdueBooks) {
        this.overdueBooks = overdueBooks;
    }

    public double getTotalFinesCollected() {
        return totalFinesCollected;
    }

    public void setTotalFinesCollected(double totalFinesCollected) {
        this.totalFinesCollected = totalFinesCollected;
    }

    public double getOutstandingFines() {
        return outstandingFines;
    }

    public void setOutstandingFines(double outstandingFines) {
        this.outstandingFines = outstandingFines;
    }

    public double getDailyFineRate() {
        return dailyFineRate;
    }

    public void setDailyFineRate(double dailyFineRate) {
        this.dailyFineRate = dailyFineRate;
    }
}
