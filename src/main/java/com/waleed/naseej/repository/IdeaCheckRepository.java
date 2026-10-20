package com.waleed.naseej.repository;

import com.waleed.naseej.entity.IdeaCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaCheckRepository extends JpaRepository<IdeaCheck, Integer> {
}
