package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Integer> {

    Optional<Contract> findByContractNumber(String contractNumber);

    boolean existsByContractNumber(String contractNumber);

    Optional<Contract> findByAccessId(Integer accessId);

    boolean existsByAccessId(Integer accessId);

    List<Contract> findByIdeaId(Integer ideaId);

    List<Contract> findByInvestorId(Integer investorId);

    List<Contract> findByStatus(ContractStatus status);

    List<Contract> findByIdeaOwnerId(Integer ideaOwnerId);
}
