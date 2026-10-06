package com.college.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Domain entity representing a Book in the library.
 * Demonstrates Inheritance (extends BaseEntity) and Encapsulation.
 */
@Entity
@Table(name = "books")
public class Book extends BaseEntity {

    @NotBlank(message = "Title is required")
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Author is required")
    @Column(nullable = false, length = 150)
    private String author;

    @NotBlank(message = "ISBN is required")
    @Size(min = 10, max = 13, message = "ISBN must be between 10 and 13 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String isbn;

    @Column(unique = true, length = 20)
    private String catalogCode;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String publisher;

    private Integer publicationYear;

    @Column(nullable = false)
    private Boolean available = true;

    private Integer totalCopies = 1;

    private Integer availableCopies = 1;

    @Column(nullable = false)
    private int issueCount = 0;

    public Book() {
    }

    public Book(String title, String author, String isbn, String category, Integer publicationYear) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.publicationYear = publicationYear;
        this.available = true;
        this.totalCopies = 1;
        this.availableCopies = 1;
    }

    // Business Logic Helper Methods
    public void markIssued() {
        if (this.availableCopies > 0) {
            this.availableCopies--;
        }
        if (this.availableCopies == 0) {
            this.available = false;
        }
    }

    public void markReturned() {
        this.availableCopies++;
        this.available = true;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCatalogCode() {
        return catalogCode;
    }

    public void setCatalogCode(String catalogCode) {
        this.catalogCode = catalogCode;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Integer getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(Integer totalCopies) {
        this.totalCopies = totalCopies;
    }

    public Integer getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(Integer availableCopies) {
        this.availableCopies = availableCopies;
        this.available = (availableCopies != null && availableCopies > 0);
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }
}
