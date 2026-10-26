package com.waleed.naseej.repository;

import com.waleed.naseej.entity.AiTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiTranslationRepository extends JpaRepository<AiTranslation, Integer> {

    Optional<AiTranslation> findBySourceTypeAndSourceIdAndLang(String sourceType, Integer sourceId, String lang);

    @Modifying
    @Query("delete from AiTranslation t where t.sourceType = :sourceType and t.sourceId = :sourceId")
    void deleteBySource(@Param("sourceType") String sourceType, @Param("sourceId") Integer sourceId);
}
