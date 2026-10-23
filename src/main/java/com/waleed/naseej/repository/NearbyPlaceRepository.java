package com.waleed.naseej.repository;

import com.waleed.naseej.entity.NearbyPlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NearbyPlaceRepository extends JpaRepository<NearbyPlace, Integer> {

    List<NearbyPlace> findByIdeaIdOrderByDistanceKmAsc(Integer ideaId);

    long countByIdeaIdAndDistanceKmLessThanEqual(Integer ideaId, Double distanceKm);

    // One DELETE statement that runs right away, so new rows for the same idea can be inserted after it
    @Modifying(flushAutomatically = true)
    @Query("delete from NearbyPlace n where n.idea.id = :ideaId")
    int deleteAllForIdea(@Param("ideaId") Integer ideaId);
}
