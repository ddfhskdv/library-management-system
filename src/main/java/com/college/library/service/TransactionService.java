package com.college.library.service;

import com.college.library.dto.IssueRequest;
import com.college.library.dto.ReturnRequest;
import com.college.library.dto.ReturnResponse;
import com.college.library.entity.Transaction;

import java.util.List;

/**
 * Interface implementing Issue & Return logic specified in Step 10.
 */
public interface TransactionService {

    /**
     * Issue book workflow:
     * Select member -> Select available book -> Issue book -> available = false -> Create transaction -> Due date = issue date + days
     */
    Transaction issueBook(IssueRequest request);

    /**
     * Return book workflow:
     * Return book -> Calculate late days -> Fine = late days * ₹5 -> Mark transaction returned -> available = true
     */
    ReturnResponse returnBook(ReturnRequest request);

    List<Transaction> getAllTransactions();

    List<Transaction> getActiveTransactions();

    List<Transaction> getOverdueTransactions();

    Transaction getTransactionById(Long id);

    Transaction payFine(Long transactionId);

    Transaction payFine(Long transactionId, String paymentMethod);

    List<Transaction> getTransactionsByMember(Long memberId);

    Transaction renewTransaction(Long transactionId, int days);
}
