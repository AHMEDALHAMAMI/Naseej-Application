package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.IdeaStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Integer> {

    long countByIdeaId(Integer ideaId);

    // Demand momentum: votes since a date
    long countByIdeaIdAndCreatedAtAfter(Integer ideaId, LocalDateTime after);

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
    // Everyone who voted for an idea (they hear when an investor adopts it)
    @Query("select v.user from Vote v where v.idea.id = :ideaId")
    List<com.waleed.naseej.entity.User> findVotersOf(@Param("ideaId") Integer ideaId);

    // [userId, votes] for the admin members list
    @Query("select v.user.id, count(v) from Vote v group by v.user.id")
    List<Object[]> countPerUser();

    long countByUserId(Integer userId);

    // A resident's votes on ideas with this status, with the idea and its neighborhood (address changes)
    @Query("select v from Vote v join fetch v.idea i join fetch i.neighborhood where v.user.id = :userId and i.status = :status")
    List<Vote> findByUserIdAndIdeaStatus(@Param("userId") Integer userId, @Param("status") IdeaStatus status);

    // Votes from before addresses existed are linked to the address made for that account
    @Modifying
    @Query("update Vote v set v.address.id = :addressId where v.user.id = :userId and v.address is null")
    void linkVotesToAddress(@Param("userId") Integer userId, @Param("addressId") Integer addressId);

    // Kept votes lose the link when their address is deleted
    @Modifying
    @Query("update Vote v set v.address = null where v.address.id = :addressId")
    void clearAddress(@Param("addressId") Integer addressId);

    // Admin deletes an account: its votes go with it
    @Modifying
    @Query("delete from Vote v where v.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
