package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Collection;
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

    // An idea is taken while it has a contract that isn't TERMINATED (expired or refunded contracts free it again)
    boolean existsByIdeaIdAndStatusNot(Integer ideaId, ContractStatus status);

    // This investor currently holds the idea (a live contract, not an ended one)
    boolean existsByIdeaIdAndInvestorIdAndStatusNot(Integer ideaId, Integer investorId, ContractStatus status);

    // Contracts past their opening deadline whose project never opened (the 180-day rule)
    List<Contract> findByStatusInAndOpenedAtIsNullAndOpeningDeadlineBefore(
            Collection<ContractStatus> statuses, LocalDate date);

    // Contracts nobody finished signing in time (the 14-day signing rule)
    List<Contract> findByStatusInAndSigningDeadlineBefore(Collection<ContractStatus> statuses, LocalDate date);
    boolean existsByInvestorIdOrIdeaOwnerId(Integer investorId, Integer ideaOwnerId);
}
