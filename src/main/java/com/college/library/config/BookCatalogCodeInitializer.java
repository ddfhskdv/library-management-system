package com.college.library.config;

import com.college.library.entity.Book;
import com.college.library.repository.BookRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookCatalogCodeInitializer {

    private final BookRepository bookRepository;

    public BookCatalogCodeInitializer(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void assignLegacyCatalogCodes() {
        List<Book> books = bookRepository.findAll();
        boolean changed = false;
        for (Book book : books) {
            String catalogCode = String.format("BK%05d", book.getId());
            if (!catalogCode.equals(book.getCatalogCode())) {
                book.setCatalogCode(catalogCode);
                changed = true;
            }
        }
        if (changed) {
            bookRepository.saveAll(books);
        }
    }
}
