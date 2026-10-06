package com.college.library.config;

import com.college.library.entity.Book;
import com.college.library.entity.Member;
import com.college.library.entity.Transaction;
import com.college.library.repository.BookRepository;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds initial demo data for college demonstration and viva.
 * Populates realistic numbers matching Step 11:
 * Total Book Copies: 100
 * Available Copies: 75
 * Issued Books: 25
 * Total Members: 50
 */
@Component
@ConditionalOnProperty(name = "library.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;

    public DataInitializer(BookRepository bookRepository,
                           MemberRepository memberRepository,
                           TransactionRepository transactionRepository) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            return; // Already initialized
        }

        System.out.println(">>> Initializing Library Demo Data for Step 10 & 11...");

        // 1. Create 50 Members
        List<Member> members = new ArrayList<>();
        String[] firstNames = {"Aarav", "Ananya", "Rohan", "Priya", "Rahul", "Neha", "Vikram", "Sneha", "Aditya", "Pooja"};
        String[] lastNames = {"Sharma", "Verma", "Patel", "Reddy", "Singh", "Kumar", "Iyer", "Nair", "Gupta", "Deshmukh"};
        String[] depts = {"Computer Science", "Information Technology", "Electronics & Comm.", "Mechanical Engg.", "Civil Engg."};

        for (int i = 1; i <= 50; i++) {
            String fname = firstNames[(i - 1) % firstNames.length];
            String lname = lastNames[((i - 1) / 5) % lastNames.length];
            String name = fname + " " + lname + (i > 10 ? " (" + i + ")" : "");
            String memId = String.format("MEM-2026-%03d", i);
            String email = "student" + i + "@college.edu";
            String phone = String.format("+91 98765 %05d", 10000 + i);
            String type = (i % 8 == 0) ? "FACULTY" : "STUDENT";
            String dept = depts[i % depts.length];

            members.add(new Member(name, email, phone, memId, type, dept));
        }
        members = memberRepository.saveAll(members);

        // 2. Create Books totaling exactly 100 copies across various standard college textbooks
        // We will seed 20 titles with 5 copies each (20 * 5 = 100 total copies)
        Object[][] bookTemplates = {
            {"Clean Code: A Handbook of Agile Software Craftsmanship", "Robert C. Martin", "9780132350884", "Software Engineering", 2008},
            {"Introduction to Algorithms (CLRS)", "Thomas H. Cormen", "9780262033848", "Computer Science", 2009},
            {"Effective Java (3rd Edition)", "Joshua Bloch", "9780134685991", "Programming", 2018},
            {"Operating System Concepts", "Abraham Silberschatz", "9781119800361", "Computer Science", 2021},
            {"Database System Concepts", "Abraham Silberschatz", "9780078022159", "Database", 2019},
            {"Computer Networks: A Systems Approach", "Larry L. Peterson", "9780123850591", "Networking", 2011},
            {"Artificial Intelligence: A Modern Approach", "Stuart Russell", "9780134610993", "Artificial Intelligence", 2020},
            {"Design Patterns: Elements of Reusable Object-Oriented Software", "Erich Gamma", "9780201633610", "Software Engineering", 1994},
            {"Head First Java", "Kathy Sierra & Bert Bates", "9780596009205", "Programming", 2005},
            {"Spring Boot in Action", "Craig Walls", "9781617292545", "Frameworks", 2016},
            {"Compilers: Principles, Techniques, and Tools (Dragon Book)", "Alfred V. Aho", "9780321486813", "Computer Science", 2006},
            {"Computer Organization and Design (MIPS Edition)", "David A. Patterson", "9780124077263", "Hardware Architecture", 2013},
            {"The Pragmatic Programmer", "David Thomas & Andrew Hunt", "9780135957059", "Software Engineering", 2019},
            {"Data Structures and Algorithms in Java", "Robert Lafore", "9780672324536", "Computer Science", 2002},
            {"Discrete Mathematics and Its Applications", "Kenneth H. Rosen", "9780073383095", "Mathematics", 2011},
            {"Python Crash Course", "Eric Matthes", "9781593279288", "Programming", 2019},
            {"Cracking the Coding Interview", "Gayle Laakmann McDowell", "9780984782857", "Career & Algorithms", 2015},
            {"Cloud Computing: Concepts, Technology & Architecture", "Thomas Erl", "9780133387520", "Cloud Computing", 2013},
            {"Modern Operating Systems", "Andrew S. Tanenbaum", "9780133591620", "Computer Science", 2014},
            {"Cryptography and Network Security", "William Stallings", "9780134444284", "Cybersecurity", 2017}
        };

        List<Book> books = new ArrayList<>();
        for (Object[] b : bookTemplates) {
            Book book = new Book((String) b[0], (String) b[1], (String) b[2], (String) b[3], (Integer) b[4]);
            book.setPublisher("Pearson / McGraw-Hill / O'Reilly");
            book.setTotalCopies(5);
            book.setAvailableCopies(5); // will be adjusted after issuing 25 books
            book.setAvailable(true);
            books.add(book);
        }
        books = bookRepository.saveAll(books);

        // 3. Issue exactly 25 books across the members to match Step 11:
        // (Total Books: 100, Issued: 25, Available: 75, Members: 50)
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 25; i++) {
            Book bookToIssue = books.get(i % books.size());
            Member member = members.get(i);

            // Deduct available copy
            bookToIssue.setAvailableCopies(bookToIssue.getAvailableCopies() - 1);
            bookToIssue.setIssueCount(bookToIssue.getIssueCount() + 1);
            if (bookToIssue.getAvailableCopies() == 0) {
                bookToIssue.setAvailable(false);
            }

            // Create transaction: make some overdue for realistic testing of fine calculation (₹5/day)
            LocalDate issueDate;
            LocalDate dueDate;
            if (i < 5) {
                // Overdue transactions: issued 20 days ago, due 6 days ago
                issueDate = today.minusDays(20);
                dueDate = today.minusDays(6);
            } else if (i < 10) {
                // Overdue transactions: issued 16 days ago, due 2 days ago
                issueDate = today.minusDays(16);
                dueDate = today.minusDays(2);
            } else {
                // Active on-time transactions: issued 3 days ago, due in 11 days
                issueDate = today.minusDays(3);
                dueDate = today.plusDays(11);
            }

            Transaction tx = new Transaction(bookToIssue, member, issueDate, dueDate);
            tx.setStatus("ISSUED");
            transactionRepository.save(tx);
        }

        // Save updated availableCopies for books
        bookRepository.saveAll(books);

        // 4. Seed a few already returned historical transactions with fine collection
        for (int i = 0; i < 3; i++) {
            Book b = books.get(i);
            Member m = members.get(30 + i);
            Transaction retTx = new Transaction(b, m, today.minusDays(30), today.minusDays(16));
            retTx.setStatus("RETURNED");
            retTx.setReturnDate(today.minusDays(12)); // 4 days late
            retTx.setFineAmount(4 * 5.0); // 4 days * ₹5 = ₹20.0 fine
            transactionRepository.save(retTx);
        }

        System.out.println(">>> Demo Data successfully populated!");
        System.out.println(">>> Total Books: 100 copies | Available: 75 | Issued: 25 | Members: 50");
    }
}
