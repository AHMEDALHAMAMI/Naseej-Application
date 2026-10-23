package com.waleed.naseej.repository;

import com.waleed.naseej.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Integer> {

    boolean existsByContractIdAndSignerRole(Integer contractId, String signerRole);

    boolean existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
            Integer contractId, String signerRole);
}
