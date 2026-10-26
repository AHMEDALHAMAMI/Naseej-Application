package com.waleed.naseej.repository;

import com.waleed.naseej.entity.FoundingMember;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundingMemberRepository extends JpaRepository<FoundingMember, Integer> {

    long countByIdeaId(Integer ideaId);

    @Modifying
    @Query("delete from FoundingMember f where f.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);

    // Used when the founder withdraws the idea
    @Modifying
    @Query("delete from FoundingMember f where f.idea.id = :ideaId")
    void deleteByIdeaId(@Param("ideaId") Integer ideaId);
}
