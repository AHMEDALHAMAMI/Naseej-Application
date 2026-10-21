package com.waleed.naseej.repository;

import com.waleed.naseej.entity.NearbyPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NearbyPlaceRepository extends JpaRepository<NearbyPlace, Integer> {
}
