package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.SubscribeRequest;
import com.waleed.naseej.dto.in.SubscriptionRequest;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.SubscriptionCheckoutResponse;
import com.waleed.naseej.dto.out.SubscriptionResponse;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.Subscription;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.enums.SubscriptionPlan;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.SubscriptionRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    // Subscription.status values
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    // Monthly prices before VAT, from the revenue model
    private static final Map<SubscriptionPlan, BigDecimal> PLAN_PRICES = Map.of(
            SubscriptionPlan.BASIC, new BigDecimal("299.00"),
            SubscriptionPlan.PRO, new BigDecimal("999.00"));

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    // ---------- Investor: subscribe, see, cancel ----------

    /**
     * Starts a monthly subscription. It stays PENDING until Moyasar confirms the payment (right away, through the
     * webhook, or when the investor opens "My subscription"), then runs for one month. Moyasar payments are
     * one-off, so there is no automatic renewal: the investor subscribes again next month.
     */
    public SubscriptionCheckoutResponse subscribe(User currentUser, SubscribeRequest request) {
        List<Subscription> mine = refreshed(getInvestor(currentUser.getId()));
        // Loaded after the refresh, which may have cleared the persistence context
        User investor = getInvestor(currentUser.getId());

        Optional<Subscription> current = mine.stream().filter(SubscriptionService::isRunning).findFirst();
        if (current.isPresent()) {
            throw new DuplicateResourceException("Your " + current.get().getPlan() + " subscription runs until "
                    + current.get().getEndsAt() + ". Subscribe again after it ends");
        }
        // Only one open checkout at a time
        mine.stream()
                .filter(subscription -> STATUS_PENDING.equals(subscription.getStatus()))
                .forEach(subscription -> {
                    subscription.setStatus(STATUS_CANCELLED);
                    subscriptionRepository.save(subscription);
                });

        SubscriptionPlan plan = request.getPlan();
        InitiateMoyasarPaymentResponse checkout = paymentService.initiateSubscriptionPayment(
                investor,
                PLAN_PRICES.get(plan),
                "Naseej " + plan.name() + " monthly subscription",
                request.getCallbackUrl(),
                request.getUseSandboxTestCard());
        Payment payment = paymentRepository.findById(checkout.getPayment().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found after checkout"));

        LocalDate today = LocalDate.now();
        Subscription subscription = Subscription.builder()
                .user(investor)
                .payment(payment)
                .plan(plan)
                .status(STATUS_PENDING)
                .startsAt(today)
                .build();
        // Moyasar may answer "paid" straight away (e.g. the sandbox card without 3DS)
        if (payment.getStatus() == PaymentStatus.PAID) {
            subscription.setStatus(STATUS_ACTIVE);
            subscription.setEndsAt(today.plusMonths(1));
        }
        return SubscriptionCheckoutResponse.builder()
                .subscription(toResponse(subscriptionRepository.save(subscription)))
                .checkout(checkout)
                .build();
    }

    /** The investor's subscriptions, newest first, after checking pending payments and ended months. */
    public List<SubscriptionResponse> mine(User currentUser) {
        User investor = getInvestor(currentUser.getId());
        return refreshed(investor).stream().map(this::toResponse).toList();
    }

    /**
     * Cancels the current subscription. A paid month keeps working until its end date (no partial refund here;
     * the admin refunds through POST /admin/payments/{id}/refund). A checkout that wasn't paid is just closed.
     */
    public SubscriptionResponse cancel(User currentUser) {
        User investor = getInvestor(currentUser.getId());
        Subscription subscription = refreshed(investor).stream()
                .filter(s -> STATUS_ACTIVE.equals(s.getStatus()) || STATUS_PENDING.equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("You have no active subscription to cancel"));
        subscription.setStatus(STATUS_CANCELLED);
        return toResponse(subscriptionRepository.save(subscription));
    }

    // Asks Moyasar about pending checkouts and marks finished months, then returns the fresh list
    private List<Subscription> refreshed(User investor) {
        // Collect the ids first: a failed sync rolls back and clears the loaded entities
        List<Integer> pendingPaymentIds = subscriptionRepository.findByUserIdOrderByIdDesc(investor.getId()).stream()
                .filter(subscription -> STATUS_PENDING.equals(subscription.getStatus())
                        && subscription.getPayment().getStatus() == PaymentStatus.INITIATED)
                .map(subscription -> subscription.getPayment().getId())
                .toList();
        for (Integer paymentId : pendingPaymentIds) {
            try {
                // Confirms the payment and activates the subscription when Moyasar says it's paid
                paymentService.syncMoyasarPayment(paymentId);
            } catch (RuntimeException notPaidYet) {
                // Still waiting for the investor to finish the checkout (or Moyasar isn't reachable)
            }
        }

        LocalDate today = LocalDate.now();
        List<Subscription> subscriptions = subscriptionRepository.findByUserIdOrderByIdDesc(investor.getId());
        for (Subscription subscription : subscriptions) {
            boolean paidMonth = STATUS_ACTIVE.equals(subscription.getStatus())
                    || STATUS_CANCELLED.equals(subscription.getStatus());
            if (paidMonth && subscription.getEndsAt() != null && subscription.getEndsAt().isBefore(today)) {
                subscription.setStatus(STATUS_EXPIRED);
                subscriptionRepository.save(subscription);
            }
        }
        return subscriptions;
    }

    // A paid month that hasn't ended, even if the investor cancelled it
    private static boolean isRunning(Subscription subscription) {
        boolean paidMonth = STATUS_ACTIVE.equals(subscription.getStatus())
                || STATUS_CANCELLED.equals(subscription.getStatus());
        return paidMonth && subscription.getEndsAt() != null && !subscription.getEndsAt().isBefore(LocalDate.now());
    }

    private User getInvestor(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getRole() != Role.INVESTOR) {
            throw new ForbiddenException("Subscriptions are for investors");
        }
        return user;
    }

    public List<SubscriptionResponse> getAll() {
        return subscriptionRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SubscriptionResponse create(SubscriptionRequest request) {
        Subscription entity = Subscription.builder()
                .user(userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getUserId())))
                .payment(paymentRepository.findById(request.getPaymentId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment not found with id: " + request.getPaymentId())))
                .plan(request.getPlan())
                .status(request.getStatus())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build();
        return toResponse(subscriptionRepository.save(entity));
    }

    public SubscriptionResponse update(Integer id, SubscriptionRequest request) {
        Subscription entity = subscriptionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));
        entity.setUser(userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId())));
        entity.setPayment(paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with id: " + request.getPaymentId())));
        entity.setPlan(request.getPlan());
        entity.setStatus(request.getStatus());
        entity.setStartsAt(request.getStartsAt());
        entity.setEndsAt(request.getEndsAt());
        return toResponse(subscriptionRepository.save(entity));
    }

    public void delete(Integer id) {
        subscriptionRepository.delete(subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id)));
    }

    public SubscriptionResponse getById(Integer id) {
        return toResponse(subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id)));
    }

    private SubscriptionResponse toResponse(Subscription entity) {
        return SubscriptionResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .paymentId(entity.getPayment().getId())
                .plan(entity.getPlan())
                .status(entity.getStatus())
                .startsAt(entity.getStartsAt())
                .endsAt(entity.getEndsAt())
                .build();
    }
}
