package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.PaymentRequest;
import com.waleed.naseej.dto.out.PaymentResponse;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public List<PaymentResponse> getAll() {
        return paymentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PaymentResponse create(PaymentRequest request) {
        if (StringUtils.hasText(request.getMoyasarPaymentId())
                && paymentRepository.existsByMoyasarPaymentId(request.getMoyasarPaymentId())) {
            throw new DuplicateResourceException(
                    "Payment already exists with moyasar payment id: " + request.getMoyasarPaymentId());
        }
        Payment entity = Payment.builder()
                .user(userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getUserId())))
                .purpose(request.getPurpose())
                .amount(request.getAmount())
                .vat(request.getVat())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "SAR")
                .method(request.getMethod())
                .moyasarPaymentId(request.getMoyasarPaymentId())
                .status(request.getStatus() != null ? request.getStatus() : PaymentStatus.INITIATED)
                .invoiceUrl(request.getInvoiceUrl())
                .paidAt(request.getPaidAt())
                .build();
        return toResponse(paymentRepository.save(entity));
    }

    public PaymentResponse update(Integer id, PaymentRequest request) {
        Payment entity = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        entity.setUser(userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getUserId())));
        entity.setPurpose(request.getPurpose());
        entity.setAmount(request.getAmount());
        entity.setVat(request.getVat());
        entity.setTotalAmount(request.getTotalAmount());
        entity.setCurrency(request.getCurrency());
        entity.setMethod(request.getMethod());
        entity.setMoyasarPaymentId(request.getMoyasarPaymentId());
        entity.setStatus(request.getStatus());
        entity.setInvoiceUrl(request.getInvoiceUrl());
        entity.setPaidAt(request.getPaidAt());
        return toResponse(paymentRepository.save(entity));
    }

    public void delete(Integer id) {
        paymentRepository.delete(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id)));
    }

    public PaymentResponse getById(Integer id) {
        return toResponse(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id)));
    }

    private PaymentResponse toResponse(Payment entity) {
        return PaymentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .purpose(entity.getPurpose())
                .amount(entity.getAmount())
                .vat(entity.getVat())
                .totalAmount(entity.getTotalAmount())
                .currency(entity.getCurrency())
                .method(entity.getMethod())
                .moyasarPaymentId(entity.getMoyasarPaymentId())
                .status(entity.getStatus())
                .invoiceUrl(entity.getInvoiceUrl())
                .paidAt(entity.getPaidAt())
                .build();
    }
}
