package com.college.library.controller;

import com.college.library.dto.ReservationRequest;
import com.college.library.entity.Reservation;
import com.college.library.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<Reservation> reserve(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.reserve(request));
    }

    @GetMapping
    public List<Reservation> getReservations(@RequestParam(required = false) Long memberId) {
        return reservationService.getActiveReservations(memberId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return ResponseEntity.ok(Map.of("message", "Reservation cancelled."));
    }
}
