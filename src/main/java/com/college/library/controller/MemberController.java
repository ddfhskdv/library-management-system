package com.college.library.controller;

import com.college.library.dto.MemberStatusRequest;
import com.college.library.entity.Member;
import com.college.library.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Member Management (Step 10 operations):
 * - Add: POST /api/members
 * - Edit: PUT /api/members/{id}
 * - Delete: DELETE /api/members/{id}
 * - Search: GET /api/members/search?query=...
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ResponseEntity<List<Member>> getAllMembers() {
        return ResponseEntity.ok(memberService.getAllMembers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Member> getMemberById(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @GetMapping("/lookup")
    public ResponseEntity<Member> lookupByStudentId(@RequestParam String code) {
        return ResponseEntity.ok(memberService.getMemberByMembershipNumber(code.trim()));
    }

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> getStudentQrCode(@PathVariable Long id) throws WriterException, IOException {
        Member member = memberService.getMemberById(id);
        BitMatrix matrix = new MultiFormatWriter().encode(member.getMembershipNumber(), BarcodeFormat.QR_CODE, 280, 280);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.IMAGE_PNG)
                .body(output.toByteArray());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Member>> searchMembers(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(memberService.searchMembers(query));
    }

    @PostMapping
    public ResponseEntity<Member> addMember(@Valid @RequestBody Member member) {
        Member saved = memberService.addMember(member);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Member> updateMember(@PathVariable Long id, @Valid @RequestBody Member member) {
        Member updated = memberService.updateMember(id, member);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.ok(Collections.singletonMap("message", "Member deleted successfully"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Member> toggleMemberStatus(@PathVariable Long id,
                                                     @Valid @RequestBody MemberStatusRequest req) {
        Member updated = memberService.toggleMemberStatus(id, req.getActive());
        return ResponseEntity.ok(updated);
    }
}
