package com.waleed.naseej.repository;

import com.waleed.naseej.entity.BusinessModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessModelRepository extends JpaRepository<BusinessModel, Integer> {

    // Loads the idea and its neighborhood in the same query (both are LAZY on the entity)
    @EntityGraph(attributePaths = {"idea", "idea.neighborhood"})
    Optional<BusinessModel> findWithIdeaById(Integer id);
}
