package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ConfirmUnlockPaymentRequest;
import com.waleed.naseej.dto.in.OwnerLaunchRequest;
import com.waleed.naseej.dto.out.CheckoutResponse;
import com.waleed.naseej.dto.out.UnlockResultResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ContractSignature;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.OpportunityAccess;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.PaymentPurpose;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PartnershipAgreementRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * An owner launching their own idea doesn't buy it: they sign the platform's service agreement and pay its one-time
 * service fee (from the configuration, + VAT) through Moyasar. The project starts only once the fee is PAID.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerLaunchTest {

    @Mock IdeaRepository ideaRepository;
    @Mock UserRepository userRepository;
    @Mock VoteRepository voteRepository;
    @Mock IdeaCheckRepository ideaCheckRepository;
    @Mock BusinessModelRepository businessModelRepository;
    @Mock FeasibilityReportRepository feasibilityReportRepository;
    @Mock OpportunityAccessRepository opportunityAccessRepository;
    @Mock PartnershipAgreementRepository partnershipAgreementRepository;
    @Mock ContractRepository contractRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentService paymentService;
    @Mock FeasibilityReportService feasibilityReportService;
    @Mock ClaudeService claudeService;
    @Mock AiTranslationService aiTranslationService;
    @Mock ContractSignatureRepository contractSignatureRepository;
    @Mock NotificationService notificationService;
    @InjectMocks UnlockService unlockService;

    private User owner;
    private Idea idea;
    private Payment fee;
    private final AtomicReference<Contract> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Neighborhood hood = Neighborhood.builder().id(1).name("Al Malqa").city("Riyadh").build();
        owner = User.builder().id(20).role(Role.INVESTOR).investorStatus(InvestorStatus.VERIFIED).fullName("Owner").build();
        idea = Idea.builder().id(7).owner(owner).neighborhood(hood).title("Car wash").status(IdeaStatus.PUBLISHED).build();
        fee = Payment.builder().id(30).user(owner).idea(idea).purpose(PaymentPurpose.OWNER_LAUNCH)
                .amount(new BigDecimal("1500.00")).vat(new BigDecimal("225.00")).totalAmount(new BigDecimal("1725.00"))
                .moyasarPaymentId("pending_x").status(PaymentStatus.INITIATED).build();
        when(userRepository.findById(20)).thenReturn(Optional.of(owner));
        when(ideaRepository.findWithNeighborhoodById(7)).thenReturn(Optional.of(idea));
        when(opportunityAccessRepository.existsByIdeaId(7)).thenReturn(false);
        when(opportunityAccessRepository.save(any(OpportunityAccess.class))).thenAnswer(inv -> {
            OpportunityAccess a = inv.getArgument(0);
            a.setId(11);
            return a;
        });
        when(contractRepository.existsByContractNumber(anyString())).thenReturn(false);
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            c.setId(5);
            saved.set(c);
            return c;
        });
        when(contractRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(paymentRepository.findById(30)).thenReturn(Optional.of(fee));
        when(paymentRepository.findFirstByUserIdAndIdeaIdAndPurposeAndStatusOrderByIdDesc(anyInt(), anyInt(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(paymentService.startFormCheckout(any(), any(), any(), anyString(), eq(PaymentPurpose.OWNER_LAUNCH)))
                .thenReturn(CheckoutResponse.builder().paymentId(30).ideaId(7).amount(new BigDecimal("1500.00"))
                        .vat(new BigDecimal("225.00")).total(new BigDecimal("1725.00")).build());
        ReflectionTestUtils.setField(unlockService, "ownerFeeSar", "1500");
    }

    private OwnerLaunchRequest request(String version) {
        return OwnerLaunchRequest.builder().termsAccepted(true).signedName("Owner Name").termsVersion(version).build();
    }

    private ConfirmUnlockPaymentRequest back() {
        return ConfirmUnlockPaymentRequest.builder().paymentId(30).build();
    }

    @Test
    void withoutAConfiguredFeeTheOwnerCantSign() {
        ReflectionTestUtils.setField(unlockService, "ownerFeeSar", "");
        assertThrows(BadRequestException.class,
                () -> unlockService.ownerCheckout(owner, 7, request(UnlockService.OWNER_TERMS_VERSION), "127.0.0.1"));
        verifyNoInteractions(paymentService);
        assertFalse(unlockService.ownerTerms(owner, 7).getAvailable());
    }

    @Test
    void theTermsShowTheFeeWithVat() {
        var terms = unlockService.ownerTerms(owner, 7);
        assertEquals(0, new BigDecimal("1500.00").compareTo(terms.getFeeAmount()));
        assertEquals(0, new BigDecimal("225.00").compareTo(terms.getFeeVat()));
        assertEquals(0, new BigDecimal("1725.00").compareTo(terms.getFeeTotal()));
    }

    @Test
    void signingAnOldVersionOfTheTermsIsRefused() {
        assertThrows(BadRequestException.class, () -> unlockService.ownerCheckout(owner, 7, request("owner-service-v1"), "127.0.0.1"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void onlyTheIdeasOwnerCanLaunchIt() {
        User other = User.builder().id(21).role(Role.INVESTOR).investorStatus(InvestorStatus.VERIFIED).build();
        when(userRepository.findById(21)).thenReturn(Optional.of(other));
        assertThrows(ForbiddenException.class,
                () -> unlockService.ownerCheckout(other, 7, request(UnlockService.OWNER_TERMS_VERSION), "127.0.0.1"));
    }

    @Test
    void signingOpensACheckoutForTheConfiguredFeeAndStartsNothing() {
        CheckoutResponse checkout = unlockService.ownerCheckout(owner, 7, request(UnlockService.OWNER_TERMS_VERSION), "127.0.0.1");

        assertEquals(30, checkout.getPaymentId());
        // The amount comes from the configuration, never from the request
        verify(paymentService).startFormCheckout(eq(owner), eq(idea), eq(new BigDecimal("1500.00")), anyString(), eq(PaymentPurpose.OWNER_LAUNCH));
        assertEquals("Owner Name", fee.getSignerName());
        assertEquals(UnlockService.OWNER_TERMS_VERSION, fee.getTermsVersion());
        assertEquals(IdeaStatus.PUBLISHED, idea.getStatus());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void anUnpaidFeeDoesntStartTheProject() {
        // Moyasar still says it isn't paid
        assertThrows(BadRequestException.class, () -> unlockService.confirmOwnerLaunch(owner, 7, back()));
        verify(contractRepository, never()).save(any());
        assertEquals(IdeaStatus.PUBLISHED, idea.getStatus());
    }

    @Test
    void aPaidFeeStartsTheProjectAndRecordsTheSignature() {
        fee.setStatus(PaymentStatus.PAID);
        fee.setSignerName("Owner Name");
        fee.setSignerIp("127.0.0.1");
        fee.setTermsVersion(UnlockService.OWNER_TERMS_VERSION);

        UnlockResultResponse result = unlockService.confirmOwnerLaunch(owner, 7, back());

        assertEquals(AccessType.OWNER, result.getAccessType());
        assertEquals(IdeaStatus.ADOPTED, idea.getStatus());
        Contract contract = saved.get();
        // A one-time fee replaced the share of the profit
        assertNull(contract.getPlatformFeePercent());
        assertEquals(UnlockService.OWNER_TERMS_VERSION, contract.getTermsVersion());

        ArgumentCaptor<OpportunityAccess> access = ArgumentCaptor.forClass(OpportunityAccess.class);
        verify(opportunityAccessRepository).save(access.capture());
        assertSame(fee, access.getValue().getPayment());

        ArgumentCaptor<ContractSignature> signature = ArgumentCaptor.forClass(ContractSignature.class);
        verify(contractSignatureRepository).save(signature.capture());
        assertEquals("IDEA_OWNER", signature.getValue().getSignerRole());
        assertEquals("Owner Name", signature.getValue().getTypedName());
        assertEquals("127.0.0.1", signature.getValue().getIpAddress());
        assertTrue(signature.getValue().isTermsAccepted());
    }

    @Test
    void aFeeCantStartTwoProjects() {
        fee.setStatus(PaymentStatus.PAID);
        when(opportunityAccessRepository.existsByPaymentId(30)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> unlockService.confirmOwnerLaunch(owner, 7, back()));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void anUnlockPaymentCantBeUsedAsTheFee() {
        fee.setPurpose(PaymentPurpose.OPPORTUNITY_ACCESS);
        fee.setStatus(PaymentStatus.PAID);
        assertThrows(BadRequestException.class, () -> unlockService.confirmOwnerLaunch(owner, 7, back()));
    }

    @Test
    void theOwnerCanLaunchBeforeTheVoteTarget() {
        when(voteRepository.countByIdeaId(7)).thenReturn(3L);
        assertTrue(unlockService.ownerTerms(owner, 7).getAvailable());
    }
}
