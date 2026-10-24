package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.enums.IdeaStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Integer> {

    // Used before deleting a neighborhood
    boolean existsByNeighborhoodId(Integer neighborhoodId);

    // Loads the neighborhood in the same query (it is LAZY on the entity)
    @EntityGraph(attributePaths = "neighborhood")
    Optional<Idea> findWithNeighborhoodById(Integer id);

    // Every other idea that isn't in screening anymore, used to look for similar ideas
    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByIdNotAndStatusNot(Integer id, IdeaStatus status);

    // Map, explore and stats: ideas in some statuses with their neighborhood
    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByStatusIn(Collection<IdeaStatus> statuses);

    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByNeighborhoodIdAndStatusIn(Integer neighborhoodId, Collection<IdeaStatus> statuses);

    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByOwnerIdOrderByIdDesc(Integer ownerId);

    long countByStatus(IdeaStatus status);
}
