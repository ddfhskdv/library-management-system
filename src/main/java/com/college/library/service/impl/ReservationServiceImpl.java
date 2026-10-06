package com.college.library.service.impl;

import com.college.library.dto.ReservationRequest;
import com.college.library.entity.Book;
import com.college.library.entity.Member;
import com.college.library.entity.Reservation;
import com.college.library.exception.ResourceNotFoundException;
import com.college.library.repository.BookRepository;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.ReservationRepository;
import com.college.library.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  BookRepository bookRepository,
                                  MemberRepository memberRepository) {
        this.reservationRepository = reservationRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public Reservation reserve(ReservationRequest request) {
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + request.getBookId()));
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + request.getMemberId()));
        if (Boolean.FALSE.equals(member.getActive())) {
            throw new IllegalStateException("Inactive members cannot reserve books.");
        }
        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            throw new IllegalStateException("This book has available copies and does not need a reservation.");
        }
        if (reservationRepository.hasActiveReservation(book.getId(), member.getId())) {
            throw new IllegalStateException("This member already has an active reservation for the book.");
        }
        return reservationRepository.save(new Reservation(book, member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> getActiveReservations(Long memberId) {
        if (memberId == null) {
            return reservationRepository.findAll().stream()
                    .filter(reservation -> "WAITING".equals(reservation.getStatus()) || "READY".equals(reservation.getStatus()))
                    .toList();
        }
        return reservationRepository.findActiveByMember(memberId);
    }

    @Override
    public void cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));
        if (!"WAITING".equals(reservation.getStatus()) && !"READY".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only waiting or ready reservations can be cancelled.");
        }
        boolean wasReady = "READY".equals(reservation.getStatus());
        reservation.setStatus("CANCELLED");
        reservationRepository.save(reservation);
        if (wasReady) {
            Book book = reservation.getBook();
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            bookRepository.save(book);
            allocateNextAvailableCopy(book);
        }
    }

    @Override
    public void allocateNextAvailableCopy(Book book) {
        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            return;
        }
        Reservation next = reservationRepository.findFirstInQueue(book.getId(), "WAITING").orElse(null);
        if (next == null) {
            return;
        }
        next.setStatus("READY");
        next.setReadyUntil(LocalDateTime.now().plusDays(2));
        reservationRepository.save(next);
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);
    }

    @Override
    public boolean claimReadyReservation(Long bookId, Long memberId) {
        Reservation ready = reservationRepository.findFirstInQueue(bookId, "READY").orElse(null);
        if (ready == null) {
            return false;
        }
        if (!ready.getMember().getId().equals(memberId)) {
            throw new IllegalStateException("A copy of this book is reserved for another member.");
        }
        if (ready.getReadyUntil() != null && ready.getReadyUntil().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("The reservation hold has expired.");
        }
        ready.setStatus("FULFILLED");
        reservationRepository.save(ready);
        return true;
    }

    @Scheduled(cron = "${library.reservations.expiry-cron:0 0 0 * * *}")
    public void expireReadyReservations() {
        List<Reservation> expired = reservationRepository.findByStatusAndReadyUntilBefore("READY", LocalDateTime.now());
        for (Reservation reservation : expired) {
            reservation.setStatus("EXPIRED");
            reservationRepository.save(reservation);
            Book book = reservation.getBook();
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            bookRepository.save(book);
            allocateNextAvailableCopy(book);
        }
    }
}
