package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // The subscription a Moyasar payment belongs to (activated when the payment is confirmed)
    Optional<Subscription> findByPaymentId(Integer paymentId);

    // A user's subscriptions, newest first
    List<Subscription> findByUserIdOrderByIdDesc(Integer userId);
}
