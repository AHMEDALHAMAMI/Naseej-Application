package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.PaymentResponse;
import com.waleed.naseej.dto.out.RefundResultResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.Subscription;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.PaymentPurpose;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Admin refund: the money goes back through Moyasar, then what it paid for is undone.
 * - Unlock payment: the investor's contract is terminated and the idea goes back to the other investors.
 *   A project that already opened can't be refunded.
 * - Subscription payment: the subscription ends today.
 * Not one transaction on purpose: once Moyasar has refunded, the REFUNDED status must stay saved.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final ContractRepository contractRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentService paymentService;
    private final ContractFlowService contractFlowService;
    private final N8nService n8nService;

    public RefundResultResponse refund(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid payments can be refunded (this one is " + payment.getStatus() + ")");
        }

        // Check what will be undone before any money moves
        Contract contract = null;
        if (PaymentPurpose.OPPORTUNITY_ACCESS.equals(payment.getPurpose())) {
            contract = opportunityAccessRepository.findByPaymentId(payment.getId())
                    .flatMap(access -> contractRepository.findByAccessId(access.getId()))
                    .orElse(null);
            if (contract != null && contract.getOpenedAt() != null) {
                throw new BadRequestException("The project already opened, so this payment can't be refunded");
            }
        }

        PaymentResponse refunded = paymentService.refundOnMoyasar(payment.getId());

        RefundResultResponse.RefundResultResponseBuilder result = RefundResultResponse.builder().payment(refunded);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("paymentId", payment.getId());
        event.put("purpose", payment.getPurpose());
        event.put("totalAmount", payment.getTotalAmount());
        event.put("userEmail", payment.getUser().getEmail());
        event.put("userName", payment.getUser().getFullName());

        String message = "Refunded through Moyasar";
        if (contract != null) {
            if (contract.getStatus() != ContractStatus.TERMINATED) {
                Idea idea = contractFlowService.endContract(contract);
                result.ideaId(idea.getId()).ideaStatus(idea.getStatus());
                event.put("ideaId", idea.getId());
                event.put("ideaTitle", idea.getTitle());
                message = "Refunded through Moyasar. The contract was terminated and the idea is open to other investors";
            }
            result.contractId(contract.getId()).contractStatus(contract.getStatus());
            event.put("contractNumber", contract.getContractNumber());
        }

        if (PaymentPurpose.SUBSCRIPTION.equals(payment.getPurpose())) {
            Subscription subscription = subscriptionRepository.findByPaymentId(payment.getId()).orElse(null);
            if (subscription != null) {
                subscription.setStatus(SubscriptionService.STATUS_REFUNDED);
                subscription.setEndsAt(LocalDate.now());
                subscriptionRepository.save(subscription);
                result.subscriptionId(subscription.getId()).subscriptionStatus(subscription.getStatus());
                message = "Refunded through Moyasar. The subscription ended today";
            }
        }

        n8nService.sendEvent("PAYMENT_REFUNDED", event);
        return result.message(message).build();
    }
}
