package com.college.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Domain entity representing a Library Member (Student/Faculty).
 * Demonstrates OOP Inheritance: Member extends Person, which in turn extends BaseEntity.
 */
@Entity
@Table(name = "members")
public class Member extends Person {

    @NotBlank(message = "Membership number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String membershipNumber;

    @Column(length = 50)
    private String membershipType = "STUDENT"; // STUDENT, FACULTY, STAFF

    @Column(length = 100)
    private String department;

    @Column(nullable = false)
    private Boolean active = true;

    private Integer maxAllowedBooks = 4;

    public Member() {
        super();
    }

    public Member(String name, String email, String phone, String membershipNumber, String membershipType, String department) {
        super(name, email, phone);
        this.membershipNumber = membershipNumber;
        this.membershipType = membershipType != null ? membershipType : "STUDENT";
        this.department = department;
        this.active = true;
        this.maxAllowedBooks = "FACULTY".equalsIgnoreCase(membershipType) ? 8 : 4;
    }

    public String getMembershipNumber() {
        return membershipNumber;
    }

    public void setMembershipNumber(String membershipNumber) {
        this.membershipNumber = membershipNumber;
    }

    public String getMembershipType() {
        return membershipType;
    }

    public void setMembershipType(String membershipType) {
        this.membershipType = membershipType;
        this.maxAllowedBooks = "FACULTY".equalsIgnoreCase(membershipType) ? 8 : 4;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getMaxAllowedBooks() {
        return maxAllowedBooks;
    }

    public void setMaxAllowedBooks(Integer maxAllowedBooks) {
        this.maxAllowedBooks = maxAllowedBooks;
    }
}
