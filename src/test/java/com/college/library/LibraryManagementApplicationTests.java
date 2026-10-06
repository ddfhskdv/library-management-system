package com.college.library;

import com.college.library.dto.IssueRequest;
import com.college.library.dto.ReturnRequest;
import com.college.library.dto.ReturnResponse;
import com.college.library.dto.ReservationRequest;
import com.college.library.entity.Book;
import com.college.library.entity.Member;
import com.college.library.entity.Transaction;
import com.college.library.service.BookService;
import com.college.library.service.FineCalculator;
import com.college.library.service.MemberService;
import com.college.library.service.TransactionService;
import com.college.library.service.ReservationService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LibraryManagementApplicationTests {

    @Autowired
    private BookService bookService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private FineCalculator fineCalculator;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Context Loads & Beans Injected")
    void contextLoads() {
        assertNotNull(bookService);
        assertNotNull(memberService);
        assertNotNull(transactionService);
        assertNotNull(fineCalculator);
    }

    @Test
    @DisplayName("Test Step 10: Fine Calculation Formula (Late Days * ₹5)")
    void testFineCalculation() {
        LocalDate due = LocalDate.now().minusDays(5);
        LocalDate returned = LocalDate.now();

        long lateDays = fineCalculator.calculateLateDays(due, returned);
        assertEquals(5, lateDays, "Late days should be 5");

        double fine = fineCalculator.calculateFine(due, returned);
        assertEquals(25.0, fine, "Fine should be 5 days * ₹5 = ₹25.0");
    }

    @Test
    @DisplayName("Test Step 10: Issue and Return Workflow")
    void testIssueAndReturnWorkflow() {
        // 1. Create a book
        Book book = new Book("Test Automation in Java", "Test Author", "9780000000001", "Testing", 2024);
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        book = bookService.addBook(book);
        assertTrue(book.getAvailable());

        // 2. Create a member
        Member member = new Member("Test Student", "test.student@college.edu", "+91 9999900000", "MEM-TEST-001", "STUDENT", "CS");
        member = memberService.addMember(member);

        // 3. Issue Book
        IssueRequest issueRequest = new IssueRequest(member.getId(), book.getId(), 14);
        Transaction tx = transactionService.issueBook(issueRequest);
        assertNotNull(tx.getId());
        assertEquals("ISSUED", tx.getStatus());

        // Verify book availability is now false
        Book issuedBook = bookService.getBookById(book.getId());
        assertEquals(0, issuedBook.getAvailableCopies());
        assertFalse(issuedBook.getAvailable());

        // 4. Return Book on-time
        ReturnRequest returnRequest = new ReturnRequest(tx.getId());
        returnRequest.setReturnDate(LocalDate.now());
        ReturnResponse retResponse = transactionService.returnBook(returnRequest);

        assertEquals(0, retResponse.getLateDays());
        assertEquals(0.0, retResponse.getFineAmount());

        // Verify book availability is restored
        Book returnedBook = bookService.getBookById(book.getId());
        assertEquals(1, returnedBook.getAvailableCopies());
        assertTrue(returnedBook.getAvailable());
    }

    @Test
    @DisplayName("Library APIs require librarian authentication")
    void apiRequiresAuthenticationAndAcceptsLibrarian() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/books").with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Duplicate ISBNs are rejected")
    void duplicateIsbnIsRejected() {
        bookService.addBook(new Book("First Test Book", "Test Author", "9780000000999", "Testing", 2026));
        Book duplicate = new Book("Duplicate Test Book", "Test Author", "9780000000999", "Testing", 2026);
        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(duplicate));
    }

    @Test
    @DisplayName("Renewals honor the configured maximum")
    void renewalsHonorMaximum() {
        Book book = bookService.addBook(new Book("Renewal Rules", "Test Author", "9780000000998", "Testing", 2026));
        Member member = memberService.addMember(new Member(
                "Renewal Test", "renewal.test@college.edu", "9999900001", "MEM-RENEWAL-001", "STUDENT", "CS"));
        Transaction tx = transactionService.issueBook(new IssueRequest(member.getId(), book.getId(), 14));
        transactionService.renewTransaction(tx.getId(), 7);
        transactionService.renewTransaction(tx.getId(), 7);
        assertThrows(IllegalStateException.class, () -> transactionService.renewTransaction(tx.getId(), 7));
    }

    @Test
    @DisplayName("Returning a reserved book holds the copy for the first member in the queue")
    void returnedCopyIsReservedForQueuedMember() {
        Book book = new Book("Reservation Queue", "Test Author", "9780000000997", "Testing", 2026);
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        book = bookService.addBook(book);
        Member first = memberService.addMember(new Member(
                "First Borrower", "first.borrower@college.edu", "9999900002", "MEM-RESERVE-001", "STUDENT", "CS"));
        Member waiting = memberService.addMember(new Member(
                "Waiting Borrower", "waiting.borrower@college.edu", "9999900003", "MEM-RESERVE-002", "STUDENT", "CS"));
        Transaction issued = transactionService.issueBook(new IssueRequest(first.getId(), book.getId(), 14));

        ReservationRequest reservationRequest = new ReservationRequest();
        reservationRequest.setBookId(book.getId());
        reservationRequest.setMemberId(waiting.getId());
        reservationService.reserve(reservationRequest);

        ReturnRequest returnRequest = new ReturnRequest(issued.getId());
        returnRequest.setReturnDate(LocalDate.now());
        transactionService.returnBook(returnRequest);
        Book heldBook = bookService.getBookById(book.getId());
        assertEquals(0, heldBook.getAvailableCopies());
        assertFalse(heldBook.getAvailable());

        Long reservedBookId = book.getId();
        Long firstMemberId = first.getId();
        assertThrows(IllegalStateException.class,
                () -> transactionService.issueBook(new IssueRequest(firstMemberId, reservedBookId, 14)));
        Transaction nextIssue = transactionService.issueBook(new IssueRequest(waiting.getId(), book.getId(), 14));
        assertEquals("ISSUED", nextIssue.getStatus());
    }

    @Test
    @DisplayName("Fine payment records the time and method")
    void finePaymentIsAudited() {
        Book book = new Book("Fine Payment Audit", "Test Author", "9780000000996", "Testing", 2026);
        Member member = new Member("Fine Test", "fine.test@college.edu", "9999900004",
                "MEM-FINE-001", "STUDENT", "CS");
        book = bookService.addBook(book);
        member = memberService.addMember(member);
        Transaction tx = transactionService.issueBook(new IssueRequest(member.getId(), book.getId(), 1));
        ReturnRequest request = new ReturnRequest(tx.getId());
        request.setReturnDate(LocalDate.now().plusDays(2));
        transactionService.returnBook(request);

        Transaction paid = transactionService.payFine(tx.getId(), "UPI");
        assertTrue(paid.isFinePaid());
        assertNotNull(paid.getFinePaidAt());
        assertEquals("UPI", paid.getFinePaymentMethod());
    }

    @Test
    @DisplayName("Librarians cannot delete catalog records")
    void deleteRequiresAdminRole() throws Exception {
        Book book = bookService.addBook(new Book("Role Guard", "Test Author", "9780000000995", "Testing", 2026));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/books/" + book.getId())
                        .with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Book labels and student QR codes are scannable PNGs")
    void catalogAndStudentCodesAreAvailableToLibrarians() throws Exception {
        Book book = bookService.addBook(new Book(
                "QR Scanner Catalog Item", "Test Author", "9780000000994", "Testing", 2026));
        assertTrue(book.getCatalogCode().matches("BK\\d{5,}"));

        Member member = memberService.addMember(new Member(
                "QR Scanner Member", "qr.scanner@college.edu", "9999900005",
                "23CSE104", "STUDENT", "CS"));

        mockMvc.perform(get("/api/books/" + book.getId() + "/qr.png")
                        .with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/books/" + book.getId() + "/barcode.png")
                        .with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/members/" + member.getId() + "/qr.png")
                        .with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/members/lookup?code=23CSE104")
                        .with(httpBasic("test-librarian", "test-librarian-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("23CSE104")));
    }

    @Test
    @DisplayName("Scan and issue UI is publicly served while its data endpoints remain protected")
    void scanWorkflowPageIsAvailableButApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));
        mockMvc.perform(get("/api/books/lookup?code=BK00001"))
                .andExpect(status().isUnauthorized());
    }
}
