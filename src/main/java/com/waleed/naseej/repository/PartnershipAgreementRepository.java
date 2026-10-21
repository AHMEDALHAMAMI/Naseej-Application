package com.waleed.naseej.repository;

import com.waleed.naseej.entity.PartnershipAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartnershipAgreementRepository extends JpaRepository<PartnershipAgreement, Integer> {
}
