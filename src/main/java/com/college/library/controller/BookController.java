package com.college.library.controller;

import com.college.library.entity.Book;
import com.college.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * REST Controller for Book Management (Step 10 operations):
 * - Add: POST /api/books
 * - Edit: PUT /api/books/{id}
 * - Delete: DELETE /api/books/{id}
 * - Search: GET /api/books/search?query=...
 * - Check availability: GET /api/books/{id}/availability
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.getBookById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(bookService.searchBooks(query));
    }

    @GetMapping("/available")
    public ResponseEntity<List<Book>> getAvailableBooks() {
        return ResponseEntity.ok(bookService.getAvailableBooks());
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(@PathVariable Long id) {
        Book book = bookService.getBookById(id);
        boolean available = bookService.checkAvailability(id);
        return ResponseEntity.ok(Map.of(
                "bookId", book.getId(),
                "title", book.getTitle(),
                "available", available,
                "availableCopies", book.getAvailableCopies(),
                "totalCopies", book.getTotalCopies()
        ));
    }

    @PostMapping
    public ResponseEntity<Book> addBook(@Valid @RequestBody Book book) {
        Book saved = bookService.addBook(book);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @Valid @RequestBody Book book) {
        Book updated = bookService.updateBook(id, book);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.ok(Collections.singletonMap("message", "Book deleted successfully"));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(bookService.getCategories());
    }

    @GetMapping("/popular")
    public ResponseEntity<List<Book>> getPopularBooks() {
        return ResponseEntity.ok(bookService.getPopularBooks());
    }

    @GetMapping("/lookup")
    public ResponseEntity<Book> lookupByBarcode(@RequestParam String code) {
        return ResponseEntity.ok(bookService.findBookByCatalogCode(code));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importBooks(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty CSV file to import.");
        }
        int imported = 0;
        List<String> rowErrors = new java.util.ArrayList<>();
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build())) {
            for (CSVRecord row : parser) {
                try {
                    Book book = new Book(
                            row.get("title").trim(),
                            row.get("author").trim(),
                            row.get("isbn").trim(),
                            row.get("category"),
                            row.get("publicationYear").isBlank() ? null : Integer.valueOf(row.get("publicationYear").trim()));
                    book.setPublisher(row.isMapped("publisher") ? row.get("publisher") : null);
                    int copies = row.isMapped("totalCopies") && !row.get("totalCopies").isBlank()
                            ? Integer.parseInt(row.get("totalCopies").trim()) : 1;
                    if (copies < 1) {
                        throw new IllegalArgumentException("totalCopies must be at least 1.");
                    }
                    book.setTotalCopies(copies);
                    book.setAvailableCopies(copies);
                    bookService.addBook(book);
                    imported++;
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    rowErrors.add("Row " + row.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("CSV must include title, author, isbn, category, publicationYear and totalCopies headers.", ex);
        }
        return ResponseEntity.ok(Map.of("imported", imported, "failed", rowErrors.size(), "errors", rowErrors));
    }

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> getBookQrCode(@PathVariable Long id) throws WriterException, IOException {
        Book book = bookService.getBookById(id);
        return pngCode(book.getCatalogCode() != null ? book.getCatalogCode() : book.getIsbn(),
                BarcodeFormat.QR_CODE, 280, 280);
    }

    @GetMapping("/{id}/barcode.png")
    public ResponseEntity<byte[]> getBookBarcode(@PathVariable Long id) throws WriterException, IOException {
        Book book = bookService.getBookById(id);
        return pngCode(book.getCatalogCode() != null ? book.getCatalogCode() : book.getIsbn(),
                BarcodeFormat.CODE_128, 520, 150);
    }

    private ResponseEntity<byte[]> pngCode(String value, BarcodeFormat format, int width, int height)
            throws WriterException, IOException {
        BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.IMAGE_PNG)
                .body(output.toByteArray());
    }
}
