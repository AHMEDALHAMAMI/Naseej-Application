package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.enums.IdeaStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Integer> {

    // Loads the neighborhood in the same query (it is LAZY on the entity)
    @EntityGraph(attributePaths = "neighborhood")
    Optional<Idea> findWithNeighborhoodById(Integer id);

    // Every other idea that isn't in screening anymore, used to look for similar ideas
    @EntityGraph(attributePaths = "neighborhood")
    List<Idea> findByIdNotAndStatusNot(Integer id, IdeaStatus status);
}
