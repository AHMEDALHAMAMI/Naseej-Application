package com.waleed.naseej.repository;

import com.waleed.naseej.entity.IdeaCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaCheckRepository extends JpaRepository<IdeaCheck, Integer> {

    @EntityGraph(attributePaths = "similarIdea")
    Optional<IdeaCheck> findByIdeaId(Integer ideaId);

    List<IdeaCheck> findByDecision(String decision);

    // Other ideas' checks that point at this idea as "similar" (cleared before the idea is deleted)
    List<IdeaCheck> findBySimilarIdeaId(Integer similarIdeaId);
}
