package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NeighborhoodRepository extends JpaRepository<Neighborhood, Integer> {

    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);

    boolean existsByNameIgnoreCaseAndCityIgnoreCaseAndIdNot(String name, String city, Integer id);

    List<Neighborhood> findByCityIgnoreCaseOrderByNameAsc(String city);

    List<Neighborhood> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}