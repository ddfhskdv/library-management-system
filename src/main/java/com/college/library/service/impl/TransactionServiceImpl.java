package com.college.library.service.impl;

import com.college.library.dto.IssueRequest;
import com.college.library.dto.ReturnRequest;
import com.college.library.dto.ReturnResponse;
import com.college.library.entity.Book;
import com.college.library.entity.Member;
import com.college.library.entity.Transaction;
import com.college.library.exception.BookNotAvailableException;
import com.college.library.exception.ResourceNotFoundException;
import com.college.library.repository.BookRepository;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.TransactionRepository;
import com.college.library.service.FineCalculator;
import com.college.library.service.ReservationService;
import com.college.library.service.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of Step 10 Issue & Return Logic:
 * Demonstrates:
 * - OOP composition and polymorphism (FineCalculator injection)
 * - ACID Transactions with @Transactional
 * - Exception Handling
 */
@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final FineCalculator fineCalculator;
    private final ReservationService reservationService;
    private final int defaultLoanDays;
    private final int maximumLoanDays;
    private final int maximumRenewals;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                  BookRepository bookRepository,
                                  MemberRepository memberRepository,
                                  FineCalculator fineCalculator,
                                  ReservationService reservationService,
                                  @Value("${library.loan.default-days:14}") int defaultLoanDays,
                                  @Value("${library.loan.max-days:60}") int maximumLoanDays,
                                  @Value("${library.loan.max-renewals:2}") int maximumRenewals) {
        this.transactionRepository = transactionRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.fineCalculator = fineCalculator;
        this.reservationService = reservationService;
        this.defaultLoanDays = defaultLoanDays;
        this.maximumLoanDays = maximumLoanDays;
        this.maximumRenewals = maximumRenewals;
    }

    /**
     * Step 10: Issue Book Logic Flow:
     * Select member -> Select available book -> Issue book -> available = false -> Create transaction -> Due date = issue date + 14 days
     */
    @Override
    public Transaction issueBook(IssueRequest request) {
        // 1. Select member
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + request.getMemberId()));

        if (Boolean.FALSE.equals(member.getActive())) {
            throw new IllegalStateException("Member account is currently inactive.");
        }

        // Enforce max allowed books limit
        long activeCount = transactionRepository.countByMemberIdAndStatus(member.getId(), "ISSUED");
        if (activeCount >= member.getMaxAllowedBooks()) {
            throw new IllegalStateException("Member has reached the maximum allowed books limit of " + member.getMaxAllowedBooks() + ".");
        }

        // 2. Select available book
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + request.getBookId()));

        // Check availability
        boolean reservedCopy = reservationService.claimReadyReservation(book.getId(), member.getId());
        if (!reservedCopy && (Boolean.FALSE.equals(book.getAvailable()) || book.getAvailableCopies() == null || book.getAvailableCopies() <= 0)) {
            throw new BookNotAvailableException("Book '" + book.getTitle() + "' is not available for issue.");
        }

        // 3. Issue book & update available
        if (!reservedCopy) {
            book.markIssued();
        }
        book.setIssueCount(book.getIssueCount() + 1);
        bookRepository.save(book);

        // 4. Create transaction
        int days = request.getLoanDays() != null ? request.getLoanDays() : defaultLoanDays;
        if (days < 1 || days > maximumLoanDays) {
            throw new IllegalArgumentException("Loan duration must be between 1 and " + maximumLoanDays + " days.");
        }
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(days);

        Transaction transaction = new Transaction(book, member, issueDate, dueDate);
        return transactionRepository.save(transaction);
    }

    /**
     * Step 10: Return Book Logic Flow:
     * Return book -> Calculate late days -> Fine = late days * ₹5 -> Mark transaction returned -> available = true
     */
    @Override
    public ReturnResponse returnBook(ReturnRequest request) {
        // 1. Retrieve transaction
        Transaction transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + request.getTransactionId()));

        if ("RETURNED".equalsIgnoreCase(transaction.getStatus())) {
            throw new IllegalStateException("This transaction has already been completed and returned.");
        }

        LocalDate returnDate = request.getReturnDate() != null ? request.getReturnDate() : LocalDate.now();
        transaction.setReturnDate(returnDate);

        // 2. Calculate late days and fine (Fine = late days * ₹5)
        long lateDays = fineCalculator.calculateLateDays(transaction.getDueDate(), returnDate);
        double fineAmount = fineCalculator.calculateFine(transaction.getDueDate(), returnDate);

        // 3. Mark transaction returned
        transaction.setStatus("RETURNED");
        transaction.setFineAmount(fineAmount);
        transactionRepository.save(transaction);

        // 4. Mark available = true & increment copies
        Book book = transaction.getBook();
        book.markReturned();
        bookRepository.save(book);
        reservationService.allocateNextAvailableCopy(book);

        String message = lateDays > 0
                ? String.format("Book returned late by %d day(s). Late fine of ₹%.2f applied.", lateDays, fineAmount)
                : "Book returned successfully on time. No fine applied.";

        return new ReturnResponse(
                transaction.getId(),
                book.getId(),
                book.getTitle(),
                transaction.getMember().getId(),
                transaction.getMember().getName(),
                transaction.getIssueDate(),
                transaction.getDueDate(),
                returnDate,
                lateDays,
                fineCalculator.getDailyRate(),
                fineAmount,
                message
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getActiveTransactions() {
        return transactionRepository.findByStatusOrderByIssueDateDesc("ISSUED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getOverdueTransactions() {
        return transactionRepository.findOverdueTransactions(LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + id));
    }

    @Override
    public Transaction payFine(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!"RETURNED".equalsIgnoreCase(tx.getStatus())) {
            throw new IllegalStateException("Cannot pay fine: transaction is not yet returned.");
        }
        if (tx.isFinePaid()) {
            throw new IllegalStateException("Fine has already been paid for this transaction.");
        }
        tx.setFinePaid(true);
        tx.setFinePaidAt(LocalDateTime.now());
        tx.setFinePaymentMethod("CASH");
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction payFine(Long transactionId, String paymentMethod) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!"RETURNED".equalsIgnoreCase(tx.getStatus())) {
            throw new IllegalStateException("Cannot pay fine: transaction is not yet returned.");
        }
        if (tx.getFineAmount() == null || tx.getFineAmount() <= 0) {
            throw new IllegalStateException("This transaction has no outstanding fine.");
        }
        if (tx.isFinePaid()) {
            throw new IllegalStateException("Fine has already been paid for this transaction.");
        }
        if (paymentMethod == null || !List.of("CASH", "CARD", "UPI", "BANK_TRANSFER")
                .contains(paymentMethod.trim().toUpperCase())) {
            throw new IllegalArgumentException("Payment method must be CASH, CARD, UPI, or BANK_TRANSFER.");
        }
        tx.setFinePaid(true);
        tx.setFinePaidAt(LocalDateTime.now());
        tx.setFinePaymentMethod(paymentMethod.trim().toUpperCase());
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByMember(Long memberId) {
        return transactionRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Override
    public Transaction renewTransaction(Long transactionId, int days) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!"ISSUED".equalsIgnoreCase(tx.getStatus())) {
            throw new IllegalStateException("Cannot renew: transaction is not currently issued.");
        }
        if (tx.getRenewalCount() >= maximumRenewals) {
            throw new IllegalStateException("This loan has reached the maximum of " + maximumRenewals + " renewals.");
        }
        if (days < 1 || days > maximumLoanDays) {
            throw new IllegalArgumentException("Renewal days must be between 1 and " + maximumLoanDays + ".");
        }
        tx.setDueDate(tx.getDueDate().plusDays(days));
        tx.setRenewalCount(tx.getRenewalCount() + 1);
        return transactionRepository.save(tx);
    }
}
