package com.waleed.naseej.repository;

import com.waleed.naseej.entity.OpportunityAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityAccessRepository extends JpaRepository<OpportunityAccess, Integer> {
}
