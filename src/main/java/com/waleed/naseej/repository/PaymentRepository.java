package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Payment;
import java.util.Optional;
import com.waleed.naseej.enums.PaymentStatus;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    boolean existsByMoyasarPaymentId(String moyasarPaymentId);

    Optional<Payment> findByMoyasarPaymentId(String moyasarPaymentId);

    // Total collected (amount before VAT) of payments in a status, 0 when none
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}
