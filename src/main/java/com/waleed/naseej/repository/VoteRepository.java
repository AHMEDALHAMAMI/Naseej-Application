package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Vote;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Integer> {

    long countByIdeaId(Integer ideaId);

    boolean existsByUserIdAndIdeaId(Integer userId, Integer ideaId);

    // [ideaId, count] for many ideas in one query (map and lists)
    @Query("select v.idea.id, count(v) from Vote v where v.idea.id in :ideaIds group by v.idea.id")
    List<Object[]> countByIdeaIds(@Param("ideaIds") Collection<Integer> ideaIds);

    // Ideas this user already voted for, out of the given ones
    @Query("select v.idea.id from Vote v where v.user.id = :userId and v.idea.id in :ideaIds")
    List<Integer> findVotedIdeaIds(@Param("userId") Integer userId, @Param("ideaIds") Collection<Integer> ideaIds);

    List<Vote> findByCreatedAtAfter(LocalDateTime after);

    // [ideaId, count] of votes cast since a moment, for "rising" requests
    @Query("select v.idea.id, count(v) from Vote v where v.createdAt >= :after group by v.idea.id")
    List<Object[]> countSince(@Param("after") LocalDateTime after);

    Optional<Vote> findByUserIdAndIdeaId(Integer userId, Integer ideaId);

    // A resident's votes with the idea and its neighborhood, newest first ("My votes")
    @EntityGraph(attributePaths = {"idea", "idea.neighborhood"})
    List<Vote> findByUserIdOrderByCreatedAtDesc(Integer userId);

    // Used when the founder withdraws the idea
    void deleteByIdeaId(Integer ideaId);
}
