package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Idea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Integer> {

    boolean existsByNeighborhoodId(Integer neighborhoodId);
}
