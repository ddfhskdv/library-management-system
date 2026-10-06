package com.college.library.service;

import com.college.library.entity.Book;
import java.util.List;

/**
 * Interface defining contract for Book Management (Step 10):
 * - Add
 * - Edit
 * - Delete
 * - Search
 * - Check availability
 */
public interface BookService {

    List<Book> getAllBooks();

    Book getBookById(Long id);

    Book findBookByCatalogCode(String code);

    Book addBook(Book book);

    Book updateBook(Long id, Book updatedBook);

    void deleteBook(Long id);

    List<Book> searchBooks(String query);

    List<Book> getAvailableBooks();

    boolean checkAvailability(Long id);

    List<String> getCategories();

    List<Book> getPopularBooks();
}
