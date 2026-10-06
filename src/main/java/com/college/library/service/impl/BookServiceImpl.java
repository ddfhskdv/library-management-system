package com.college.library.service.impl;

import com.college.library.entity.Book;
import com.college.library.exception.ResourceNotFoundException;
import com.college.library.repository.BookRepository;
import com.college.library.repository.TransactionRepository;
import com.college.library.service.BookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of BookService demonstrating:
 * - OOP Encapsulation & Polymorphism
 * - Java Collections & Stream API
 * - Exception Handling
 * - JPA Repository queries
 */
@Service
@Transactional
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final TransactionRepository transactionRepository;

    public BookServiceImpl(BookRepository bookRepository, TransactionRepository transactionRepository) {
        this.bookRepository = bookRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Book findBookByCatalogCode(String code) {
        String normalized = code == null ? "" : code.trim();
        return bookRepository.findByCatalogCodeIgnoreCaseOrIsbn(normalized, normalized)
                .orElseThrow(() -> new ResourceNotFoundException("No book matches barcode or ISBN: " + normalized));
    }

    @Override
    public Book addBook(Book book) {
        if (book.getTotalCopies() == null || book.getTotalCopies() <= 0) {
            book.setTotalCopies(1);
        }
        if (book.getAvailableCopies() == null) {
            book.setAvailableCopies(book.getTotalCopies());
        }
        book.setAvailable(book.getAvailableCopies() > 0);
        if (book.getIsbn() != null && !book.getIsbn().isBlank() && bookRepository.existsByIsbn(book.getIsbn())) {
            throw new IllegalArgumentException("A book with ISBN '" + book.getIsbn() + "' already exists.");
        }
        Book saved = bookRepository.save(book);
        saved.setCatalogCode(String.format("BK%05d", saved.getId()));
        return bookRepository.save(saved);
    }

    @Override
    public Book updateBook(Long id, Book updatedBook) {
        Book existing = getBookById(id);
        if (updatedBook.getIsbn() != null && !updatedBook.getIsbn().isBlank()
                && !updatedBook.getIsbn().equals(existing.getIsbn())
                && bookRepository.existsByIsbn(updatedBook.getIsbn())) {
            throw new IllegalArgumentException("A book with ISBN '" + updatedBook.getIsbn() + "' already exists.");
        }
        existing.setTitle(updatedBook.getTitle());
        existing.setAuthor(updatedBook.getAuthor());
        existing.setIsbn(updatedBook.getIsbn());
        existing.setCoverImageUrl(updatedBook.getCoverImageUrl());
        existing.setCategory(updatedBook.getCategory());
        existing.setPublisher(updatedBook.getPublisher());
        existing.setPublicationYear(updatedBook.getPublicationYear());
        if (updatedBook.getTotalCopies() != null) {
            int updatedAvailableCopies = existing.getAvailableCopies()
                    + updatedBook.getTotalCopies() - existing.getTotalCopies();
            if (updatedAvailableCopies < 0) {
                throw new IllegalArgumentException("Total copies cannot be less than the number of issued and reserved copies.");
            }
            existing.setTotalCopies(updatedBook.getTotalCopies());
            existing.setAvailableCopies(updatedAvailableCopies);
        }
        return bookRepository.save(existing);
    }

    @Override
    public void deleteBook(Long id) {
        Book book = getBookById(id);
        // Prevent deleting a book if it is currently issued
        boolean hasActiveIssues = transactionRepository.findFirstByBookIdAndStatus(id, "ISSUED").isPresent();
        if (hasActiveIssues) {
            throw new IllegalStateException("Cannot delete book '" + book.getTitle() + "' because it is currently issued to a member.");
        }
        bookRepository.delete(book);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return bookRepository.findAll();
        }
        return bookRepository.searchBooks(query.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> getAvailableBooks() {
        return bookRepository.findByAvailable(true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkAvailability(Long id) {
        Book book = getBookById(id);
        return Boolean.TRUE.equals(book.getAvailable()) && book.getAvailableCopies() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return bookRepository.findDistinctCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> getPopularBooks() {
        return bookRepository.findTop5ByOrderByIssueCountDesc();
    }
}
