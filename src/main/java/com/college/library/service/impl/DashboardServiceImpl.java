package com.college.library.service.impl;

import com.college.library.dto.DashboardStats;
import com.college.library.entity.Book;
import com.college.library.repository.BookRepository;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.TransactionRepository;
import com.college.library.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Step 11 - Computes dashboard metrics dynamically via Java APIs.
 * Demonstrates Java Streams and aggregation across repositories.
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final double dailyFineRate;

    public DashboardServiceImpl(BookRepository bookRepository,
                                MemberRepository memberRepository,
                                TransactionRepository transactionRepository,
                                @Value("${library.fine.daily-rate:5.0}") double dailyFineRate) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
        this.dailyFineRate = dailyFineRate;
    }

    @Override
    public DashboardStats getDashboardStatistics() {
        List<Book> books = bookRepository.findAll();

        // Calculate total book copies dynamically using Java Streams
        long totalCopies = books.stream()
                .mapToLong(b -> b.getTotalCopies() != null ? b.getTotalCopies() : 1)
                .sum();

        long availableCopies = books.stream()
                .mapToLong(b -> b.getAvailableCopies() != null ? b.getAvailableCopies() : (Boolean.TRUE.equals(b.getAvailable()) ? 1 : 0))
                .sum();

        long issuedCount = transactionRepository.countByStatus("ISSUED");
        long totalMembers = memberRepository.count();
        long overdueCount = transactionRepository.countOverdueTransactions(LocalDate.now());
        Double totalFines = transactionRepository.sumTotalFines();
        Double outstandingFines = transactionRepository.sumOutstandingFines();

        return new DashboardStats(
                totalCopies,
                availableCopies,
                issuedCount,
                totalMembers,
                overdueCount,
                totalFines != null ? totalFines : 0.0,
                outstandingFines != null ? outstandingFines : 0.0,
                dailyFineRate
        );
    }
}
