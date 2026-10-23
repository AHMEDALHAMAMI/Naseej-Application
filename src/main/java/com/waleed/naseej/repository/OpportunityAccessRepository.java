package com.waleed.naseej.repository;

import com.waleed.naseej.entity.OpportunityAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityAccessRepository extends JpaRepository<OpportunityAccess, Integer> {

    boolean existsByInvestorIdAndIdeaId(Integer investorId, Integer ideaId);

    boolean existsByPaymentId(Integer paymentId);

    // Used by PaymentService after a Moyasar payment to find the linked contract
    Optional<OpportunityAccess> findByPaymentId(Integer paymentId);

    List<OpportunityAccess> findByInvestorId(Integer investorId);

    long countByIdeaId(Integer ideaId);
}