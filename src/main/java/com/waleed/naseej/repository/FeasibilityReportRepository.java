package com.waleed.naseej.repository;

import com.waleed.naseej.entity.FeasibilityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeasibilityReportRepository extends JpaRepository<FeasibilityReport, Integer> {
}
