package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.SubscriptionRequest;
import com.waleed.naseej.dto.out.SubscriptionResponse;
import com.waleed.naseej.entity.Subscription;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.SubscriptionRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

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
