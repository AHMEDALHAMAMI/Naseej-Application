package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProfileUpdateRequest;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @InjectMocks ProfileService profileService;

    private User investor;

    @BeforeEach
    void setUp() {
        investor = User.builder().id(3).role(Role.INVESTOR).crNumber("7010101010")
                .investorStatus(InvestorStatus.VERIFIED).investorReviewedAt(LocalDateTime.now()).build();
        when(userRepository.findById(3)).thenReturn(Optional.of(investor));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newCrSendsAVerifiedInvestorBackToReview() {
        profileService.update(investor, ProfileUpdateRequest.builder().crNumber("7020202020").build());
        assertEquals(InvestorStatus.PENDING, investor.getInvestorStatus());
        assertNull(investor.getInvestorReviewedAt());
    }

    @Test
    void sameCrKeepsTheVerification() {
        profileService.update(investor, ProfileUpdateRequest.builder().crNumber("7010101010").companyName("Nakheel Co").build());
        assertEquals(InvestorStatus.VERIFIED, investor.getInvestorStatus());
        assertEquals("Nakheel Co", investor.getCompanyName());
    }

    @Test
    void budgetRangeIsSavedWithTheMatchingOlderBucket() {
        profileService.update(investor, ProfileUpdateRequest.builder()
                .budgetMin(new BigDecimal("200000")).budgetMax(new BigDecimal("650000")).build());
        assertEquals(new BigDecimal("650000"), investor.getBudgetMax());
        assertEquals("FROM_300K_TO_700K", investor.getInvestmentBudget());

        profileService.update(investor, ProfileUpdateRequest.builder()
                .budgetMin(new BigDecimal("1000000")).budgetNoMax(true).build());
        assertNull(investor.getBudgetMax());
        assertEquals("ABOVE_700K", investor.getInvestmentBudget());
    }

    @Test
    void minimumAboveMaximumIsRefused() {
        assertThrows(BadRequestException.class, () -> profileService.update(investor, ProfileUpdateRequest.builder()
                .budgetMin(new BigDecimal("900000")).budgetMax(new BigDecimal("300000")).build()));
    }

    @Test
    void residentsCantSendInvestorFields() {
        User resident = User.builder().id(4).role(Role.RESIDENT).build();
        when(userRepository.findById(4)).thenReturn(Optional.of(resident));
        assertThrows(BadRequestException.class, () -> profileService.update(resident,
                ProfileUpdateRequest.builder().companyName("x").build()));
    }

    @Test
    void olderBucketsBecomeRanges() {
        assertEquals(new BigDecimal("300000"), ProfileService.bucketMax("UP_TO_300K"));
        assertEquals(new BigDecimal("700000"), ProfileService.bucketMin("ABOVE_700K"));
        assertNull(ProfileService.bucketMax("ABOVE_700K"));
    }
}
