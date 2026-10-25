package com.waleed.naseej.repository;

import com.waleed.naseej.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Integer> {

    boolean existsByContractIdAndSignerRole(Integer contractId, String signerRole);

    boolean existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
            Integer contractId, String signerRole);

    // Both signatures of a contract, first signer first (contract page and "My projects")
    List<ContractSignature> findByContractIdOrderBySignedAtAsc(Integer contractId);
}
