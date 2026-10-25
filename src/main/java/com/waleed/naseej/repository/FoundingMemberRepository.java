package com.waleed.naseej.repository;

import com.waleed.naseej.entity.FoundingMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundingMemberRepository extends JpaRepository<FoundingMember, Integer> {

    long countByIdeaId(Integer ideaId);

    // Used when the founder withdraws the idea
    void deleteByIdeaId(Integer ideaId);
}
