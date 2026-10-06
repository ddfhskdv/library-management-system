package com.college.library.controller;

import com.college.library.entity.Book;
import com.college.library.entity.Member;
import com.college.library.entity.Transaction;
import com.college.library.repository.BookRepository;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final double dailyFineRate;

    public ReportController(BookRepository bookRepository,
                            MemberRepository memberRepository,
                            TransactionRepository transactionRepository,
                            @Value("${library.fine.daily-rate:5.0}") double dailyFineRate) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
        this.dailyFineRate = dailyFineRate;
    }

    @GetMapping(value = "/books.csv", produces = "text/csv")
    public ResponseEntity<byte[]> books() throws IOException {
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT.builder()
                .setHeader("id", "catalogCode", "title", "author", "isbn", "category", "publisher",
                        "publicationYear", "totalCopies", "availableCopies", "issueCount")
                .build())) {
            for (Book book : bookRepository.findAll()) {
                printer.printRecord(book.getId(), book.getCatalogCode(), book.getTitle(), book.getAuthor(),
                        book.getIsbn(), book.getCategory(), book.getPublisher(), book.getPublicationYear(),
                        book.getTotalCopies(), book.getAvailableCopies(), book.getIssueCount());
            }
        }
        return csvResponse("books.csv", csv.toString());
    }

    @GetMapping(value = "/members.csv", produces = "text/csv")
    public ResponseEntity<byte[]> members() throws IOException {
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT.builder()
                .setHeader("id", "name", "email", "phone", "membershipNumber", "membershipType",
                        "department", "active")
                .build())) {
            for (Member member : memberRepository.findAll()) {
                printer.printRecord(member.getId(), member.getName(), member.getEmail(), member.getPhone(),
                        member.getMembershipNumber(), member.getMembershipType(), member.getDepartment(), member.getActive());
            }
        }
        return csvResponse("members.csv", csv.toString());
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<byte[]> transactions() throws IOException {
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT.builder()
                .setHeader("id", "bookCode", "bookTitle", "memberNumber", "memberName", "issueDate",
                        "dueDate", "returnDate", "status", "fineAmount", "finePaid", "finePaidAt", "paymentMethod")
                .build())) {
            for (Transaction tx : transactionRepository.findAllByOrderByCreatedAtDesc()) {
                printer.printRecord(tx.getId(), tx.getBook().getCatalogCode(), tx.getBook().getTitle(),
                        tx.getMember().getMembershipNumber(), tx.getMember().getName(), tx.getIssueDate(),
                        tx.getDueDate(), tx.getReturnDate(), tx.getStatus(), tx.getFineAmount(),
                        tx.isFinePaid(), tx.getFinePaidAt(), tx.getFinePaymentMethod());
            }
        }
        return csvResponse("transactions.csv", csv.toString());
    }

    @GetMapping(value = "/overdue.csv", produces = "text/csv")
    public ResponseEntity<byte[]> overdue() throws IOException {
        List<Transaction> overdue = transactionRepository.findOverdueTransactions(LocalDate.now());
        StringWriter csv = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(csv, CSVFormat.DEFAULT.builder()
                .setHeader("transactionId", "bookTitle", "memberNumber", "memberName", "email",
                        "dueDate", "daysOverdue", "estimatedFine")
                .build())) {
            for (Transaction tx : overdue) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(tx.getDueDate(), LocalDate.now());
                printer.printRecord(tx.getId(), tx.getBook().getTitle(), tx.getMember().getMembershipNumber(),
                        tx.getMember().getName(), tx.getMember().getEmail(), tx.getDueDate(), days, days * dailyFineRate);
            }
        }
        return csvResponse("overdue-report.csv", csv.toString());
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
