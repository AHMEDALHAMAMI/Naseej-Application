package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.VoteRequest;
import com.waleed.naseej.entity.Address;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.repository.IdeaRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Voting eligibility comes from saved addresses only; the device's coordinates never count. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscoverVoteTest {

    @Mock UserRepository userRepository;
    @Mock IdeaRepository ideaRepository;
    @Mock VoteRepository voteRepository;
    @Mock AddressService addressService;
    @Mock AiTranslationService aiTranslationService;
    @Mock NotificationService notificationService;
    @Mock ProjectService projectService;
    // Used while building the idea page after a vote
    @Mock com.waleed.naseej.repository.NeighborhoodRepository neighborhoodRepository;
    @Mock com.waleed.naseej.repository.FoundingMemberRepository foundingMemberRepository;
    @Mock com.waleed.naseej.repository.IdeaCheckRepository ideaCheckRepository;
    @Mock com.waleed.naseej.repository.BusinessModelRepository businessModelRepository;
    @Mock com.waleed.naseej.repository.FeasibilityReportRepository feasibilityReportRepository;
    @Mock com.waleed.naseej.repository.OpportunityAccessRepository opportunityAccessRepository;
    @Mock IdeaCheckService ideaCheckService;
    @Mock BusinessModelService businessModelService;
    @Mock FeasibilityReportService feasibilityReportService;
    @Mock NearbyPlaceService nearbyPlaceService;
    @Mock LocationService locationService;
    @InjectMocks DiscoverService discoverService;

    private User resident;
    private Idea idea;
    private Neighborhood hood;

    @BeforeEach
    void setUp() {
        hood = Neighborhood.builder().id(1).name("Al Narjis").city("Riyadh").latitude(24.8270).longitude(46.6540).build();
        User owner = User.builder().id(2).role(Role.RESIDENT).build();
        resident = User.builder().id(7).role(Role.RESIDENT).latitude(24.40).longitude(46.20).build();
        idea = Idea.builder().id(5).owner(owner).neighborhood(hood).status(IdeaStatus.PUBLISHED)
                .latitude(24.8270).longitude(46.6560).membershipThreshold(150).build();
        when(userRepository.findById(7)).thenReturn(Optional.of(resident));
        when(ideaRepository.findWithNeighborhoodById(5)).thenReturn(Optional.of(idea));
        when(projectService.stageOfIdea(any())).thenReturn(Optional.empty());
    }

    private Address address(int id, double lat, double lng, boolean primary) {
        return Address.builder().id(id).user(resident).latitude(lat).longitude(lng).neighborhood(hood)
                .primaryAddress(primary).needsConfirmation(false).build();
    }

    @Test
    void coordinatesNextToTheIdeaDontMakeAFarResidentEligible() {
        // Home is about 65 km away; the request claims to be standing at the idea
        when(addressService.addressesOf(resident)).thenReturn(List.of(address(10, 24.40, 46.20, true)));
        VoteRequest atTheIdea = VoteRequest.builder().latitude(24.8270).longitude(46.6560).build();

        BadRequestException e = assertThrows(BadRequestException.class, () -> discoverService.vote(resident, 5, atTheIdea));
        assertTrue(e.getMessage().startsWith("You can vote only for ideas within 8 km of your saved addresses"));
        verify(voteRepository, never()).save(any());
        // The saved location wasn't touched either
        assertEquals(24.40, resident.getLatitude());
        verify(userRepository, never()).save(any());
    }

    @Test
    void secondHomeInRangeMakesTheResidentEligibleAndIsRecorded() {
        Address far = address(10, 24.40, 46.20, true);
        Address near = address(11, 24.8300, 46.6500, false);
        when(addressService.addressesOf(resident)).thenReturn(List.of(far, near));

        discoverService.vote(resident, 5, null);

        ArgumentCaptor<Vote> saved = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(saved.capture());
        assertEquals(11, saved.getValue().getAddress().getId());
        assertTrue(saved.getValue().getDistanceKm() < 2.0);
    }

    @Test
    void anAddressThatNeedsConfirmingBlocksVoting() {
        Address home = address(10, 24.8270, 46.6540, true);
        home.setNeedsConfirmation(true);
        when(addressService.addressesOf(resident)).thenReturn(List.of(home));

        BadRequestException e = assertThrows(BadRequestException.class, () -> discoverService.vote(resident, 5, null));
        assertEquals("Confirm your home address in your account to vote", e.getMessage());
    }

    @Test
    void noAddressMeansNoVote() {
        when(addressService.addressesOf(resident)).thenReturn(List.of());
        BadRequestException e = assertThrows(BadRequestException.class, () -> discoverService.vote(resident, 5, null));
        assertEquals("Add your home address in your account to vote", e.getMessage());
    }
}
