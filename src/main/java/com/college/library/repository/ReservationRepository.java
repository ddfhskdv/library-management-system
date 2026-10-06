package com.college.library.repository;

import com.college.library.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("SELECT r FROM Reservation r WHERE r.book.id = :bookId AND r.status = :status ORDER BY r.createdAt ASC")
    Optional<Reservation> findFirstInQueue(@Param("bookId") Long bookId, @Param("status") String status);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r " +
            "WHERE r.book.id = :bookId AND r.member.id = :memberId AND r.status IN ('WAITING', 'READY')")
    boolean hasActiveReservation(@Param("bookId") Long bookId, @Param("memberId") Long memberId);

    @Query("SELECT r FROM Reservation r WHERE r.member.id = :memberId AND r.status IN ('WAITING', 'READY') ORDER BY r.createdAt ASC")
    List<Reservation> findActiveByMember(@Param("memberId") Long memberId);

    List<Reservation> findByStatusAndReadyUntilBefore(String status, LocalDateTime time);
}
