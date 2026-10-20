package com.waleed.naseej.repository;

import com.waleed.naseej.entity.ProjectUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectUpdateRepository extends JpaRepository<ProjectUpdate, Integer> {
}
