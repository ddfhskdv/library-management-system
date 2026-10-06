package com.college.library.dto;

import jakarta.validation.constraints.NotNull;

public class IssueRequest {

    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Book ID is required")
    private Long bookId;

    private Integer loanDays = 14; // Default standard loan period: 14 days

    public IssueRequest() {
    }

    public IssueRequest(Long memberId, Long bookId, Integer loanDays) {
        this.memberId = memberId;
        this.bookId = bookId;
        this.loanDays = loanDays != null ? loanDays : 14;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Integer getLoanDays() {
        return loanDays;
    }

    public void setLoanDays(Integer loanDays) {
        this.loanDays = loanDays;
    }
}
