package com.college.library.controller;

import com.college.library.dto.IssueRequest;
import com.college.library.dto.ReturnRequest;
import com.college.library.dto.ReturnResponse;
import com.college.library.entity.Transaction;
import com.college.library.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Step 10 Issue & Return transactions.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Step 10: Issue Book endpoint
     * Select member -> Select available book -> Issue book -> available = false -> Create transaction -> Due date
     */
    @PostMapping("/issue")
    public ResponseEntity<Transaction> issueBook(@Valid @RequestBody IssueRequest request) {
        Transaction transaction = transactionService.issueBook(request);
        return new ResponseEntity<>(transaction, HttpStatus.CREATED);
    }

    /**
     * Step 10: Return Book endpoint
     * Return book -> Calculate late days -> Fine = late days * ₹5 -> Mark transaction returned -> available = true
     */
    @PostMapping("/return")
    public ResponseEntity<ReturnResponse> returnBook(@Valid @RequestBody ReturnRequest request) {
        ReturnResponse response = transactionService.returnBook(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Transaction>> getActiveTransactions() {
        return ResponseEntity.ok(transactionService.getActiveTransactions());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Transaction>> getOverdueTransactions() {
        return ResponseEntity.ok(transactionService.getOverdueTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @PostMapping("/{id}/pay-fine")
    public ResponseEntity<Transaction> payFine(@PathVariable Long id,
                                               @RequestParam(defaultValue = "CASH") String method) {
        return ResponseEntity.ok(transactionService.payFine(id, method));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<Transaction>> getTransactionsByMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(transactionService.getTransactionsByMember(memberId));
    }

    @PutMapping("/{id}/renew")
    public ResponseEntity<Transaction> renewTransaction(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(transactionService.renewTransaction(id, days));
    }
}
