package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Address;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {

    // Primary first, then the order they were added; the neighborhood comes in the same query
    @EntityGraph(attributePaths = "neighborhood")
    List<Address> findByUserIdOrderByPrimaryAddressDescIdAsc(Integer userId);

    long countByUserId(Integer userId);

    boolean existsByNeighborhoodId(Integer neighborhoodId);

    // Admin deletes an account: its addresses go with it
    @Modifying
    @Query("delete from Address a where a.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
