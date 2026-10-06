package com.college.library.service.impl;

import com.college.library.entity.Member;
import com.college.library.exception.ResourceNotFoundException;
import com.college.library.repository.MemberRepository;
import com.college.library.repository.TransactionRepository;
import com.college.library.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of MemberService demonstrating OOP Encapsulation and Data validation.
 */
@Service
@Transactional
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;

    public MemberServiceImpl(MemberRepository memberRepository, TransactionRepository transactionRepository) {
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Member getMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Member getMemberByMembershipNumber(String membershipNumber) {
        return memberRepository.findByMembershipNumber(membershipNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with membership number: " + membershipNumber));
    }

    @Override
    public Member addMember(Member member) {
        if (memberRepository.existsByMembershipNumber(member.getMembershipNumber())) {
            throw new IllegalArgumentException("Membership Number already exists: " + member.getMembershipNumber());
        }
        return memberRepository.save(member);
    }

    @Override
    public Member updateMember(Long id, Member updatedMember) {
        Member existing = getMemberById(id);
        existing.setName(updatedMember.getName());
        existing.setEmail(updatedMember.getEmail());
        existing.setPhone(updatedMember.getPhone());
        existing.setMembershipType(updatedMember.getMembershipType());
        existing.setDepartment(updatedMember.getDepartment());
        existing.setActive(updatedMember.getActive());
        return memberRepository.save(existing);
    }

    @Override
    public void deleteMember(Long id) {
        Member member = getMemberById(id);
        // Prevent deleting a member with unreturned books
        boolean hasActiveIssues = !transactionRepository.findByMemberIdAndStatus(id, "ISSUED").isEmpty();
        if (hasActiveIssues) {
            throw new IllegalStateException("Cannot delete member '" + member.getName() + "' because they have active issued books.");
        }
        memberRepository.delete(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> searchMembers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return memberRepository.findAll();
        }
        return memberRepository.searchMembers(query.trim());
    }

    @Override
    public Member toggleMemberStatus(Long id, Boolean active) {
        Member member = getMemberById(id);
        member.setActive(active);
        return memberRepository.save(member);
    }
}
