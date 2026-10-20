package com.waleed.naseej.repository;

import com.waleed.naseej.entity.BusinessModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessModelRepository extends JpaRepository<BusinessModel, Integer> {
}
