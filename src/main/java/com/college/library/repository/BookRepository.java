package com.college.library.repository;

import com.college.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Book entities.
 * Demonstrates abstraction and dynamic query derivation.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByAvailable(Boolean available);

    long countByAvailable(Boolean available);

    @Query("SELECT b FROM Book b WHERE " +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(b.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(b.isbn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(b.catalogCode) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Book> searchBooks(@Param("query") String query);

    List<Book> findByCategoryIgnoreCase(String category);

    boolean existsByIsbn(String isbn);

    Optional<Book> findByCatalogCodeIgnoreCaseOrIsbn(String catalogCode, String isbn);

    @Query("SELECT DISTINCT b.category FROM Book b WHERE b.category IS NOT NULL AND b.category <> '' ORDER BY b.category")
    List<String> findDistinctCategories();

    List<Book> findTop5ByOrderByIssueCountDesc();
}
