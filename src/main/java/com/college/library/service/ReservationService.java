package com.college.library.service;

import com.college.library.dto.ReservationRequest;
import com.college.library.entity.Book;
import com.college.library.entity.Reservation;

import java.util.List;

public interface ReservationService {

    Reservation reserve(ReservationRequest request);

    List<Reservation> getActiveReservations(Long memberId);

    void cancel(Long reservationId);

    void allocateNextAvailableCopy(Book book);

    boolean claimReadyReservation(Long bookId, Long memberId);
}
