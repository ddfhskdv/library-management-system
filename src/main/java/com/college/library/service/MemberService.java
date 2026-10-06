package com.college.library.service;

import com.college.library.entity.Member;
import java.util.List;

/**
 * Interface defining contract for Member Management (Step 10):
 * - Add
 * - Edit
 * - Delete
 * - Search
 */
public interface MemberService {

    List<Member> getAllMembers();

    Member getMemberById(Long id);

    Member getMemberByMembershipNumber(String membershipNumber);

    Member addMember(Member member);

    Member updateMember(Long id, Member updatedMember);

    void deleteMember(Long id);

    List<Member> searchMembers(String query);

    Member toggleMemberStatus(Long id, Boolean active);
}
