package com.college.library.repository;

import com.college.library.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Issue/Return Transactions.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatusOrderByIssueDateDesc(String status);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    Optional<Transaction> findFirstByBookIdAndStatus(Long bookId, String status);

    List<Transaction> findByMemberIdAndStatus(Long memberId, String status);

    @Query("SELECT t FROM Transaction t WHERE t.status = 'ISSUED' AND t.dueDate < :currentDate")
    List<Transaction> findOverdueTransactions(@Param("currentDate") LocalDate currentDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = 'ISSUED' AND t.dueDate < :currentDate")
    long countOverdueTransactions(@Param("currentDate") LocalDate currentDate);

    @Query("SELECT COALESCE(SUM(t.fineAmount), 0.0) FROM Transaction t WHERE t.status = 'RETURNED' AND t.finePaid = true")
    Double sumTotalFines();

    @Query("SELECT COALESCE(SUM(t.fineAmount), 0.0) FROM Transaction t WHERE t.status = 'RETURNED' AND t.finePaid = false")
    Double sumOutstandingFines();

    List<Transaction> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    long countByMemberIdAndStatus(Long memberId, String status);

    long countByBookIdAndStatus(Long bookId, String status);

    List<Transaction> findByStatusAndDueDateBetweenOrderByDueDateAsc(
            String status, LocalDate startDate, LocalDate endDate);
}
