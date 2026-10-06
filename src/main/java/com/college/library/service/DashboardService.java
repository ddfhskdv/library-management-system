package com.college.library.service;

import com.college.library.dto.DashboardStats;

/**
 * Interface defining contract for Step 11 dynamic dashboard metrics.
 */
public interface DashboardService {

    /**
     * Dynamically computes:
     * - Total Books
     * - Available Books
     * - Issued Books
     * - Total Members
     * - Overdue loans & total fines
     */
    DashboardStats getDashboardStatistics();
}
