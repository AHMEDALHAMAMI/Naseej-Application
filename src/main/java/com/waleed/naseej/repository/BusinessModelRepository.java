package com.waleed.naseej.repository;

import com.waleed.naseej.entity.BusinessModel;
import com.waleed.naseej.enums.IdeaStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessModelRepository extends JpaRepository<BusinessModel, Integer> {

    // Loads the idea and its neighborhood in the same query (both are LAZY on the entity)
    @EntityGraph(attributePaths = {"idea", "idea.neighborhood"})
    Optional<BusinessModel> findWithIdeaById(Integer id);

    Optional<BusinessModel> findByIdeaId(Integer ideaId);

    boolean existsByIdeaId(Integer ideaId);

    // Business models whose minimum capital fits the budget, skipping ideas still in screening
    List<BusinessModel> findByEstimatedCapitalMinLessThanEqualAndIdeaStatusNotOrderByEstimatedCapitalMinAsc(
            BigDecimal max, IdeaStatus status);

    // The business models of many ideas in one query (investor search by capital)
    List<BusinessModel> findByIdeaIdIn(Collection<Integer> ideaIds);
}
