package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.enums.IdeaStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Integer> {

    // Used before deleting a neighborhood
    boolean existsByNeighborhoodId(Integer neighborhoodId);

    // Loads the neighborhood in the same query (it is LAZY on the entity)
    @EntityGraph(attributePaths = "neighborhood")
    Optional<Idea> findWithNeighborhoodById(Integer id);

    // Map, explore, stats and the similar-ideas check: ideas in some statuses with their neighborhood
    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByStatusIn(Collection<IdeaStatus> statuses);

    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByNeighborhoodIdAndStatusIn(Integer neighborhoodId, Collection<IdeaStatus> statuses);

    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByOwnerIdOrderByIdDesc(Integer ownerId);

    long countByStatus(IdeaStatus status);

    // [ownerId, ideas] for the admin members list
    @Query("select i.owner.id, count(i) from Idea i group by i.owner.id")
    List<Object[]> countPerOwner();

    boolean existsByOwnerId(Integer ownerId);

    long countByOwnerId(Integer ownerId);
}
