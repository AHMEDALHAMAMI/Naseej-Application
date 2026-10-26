package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProposeIdeaRequest;
import com.waleed.naseej.dto.out.IdeaCheckResponse;
import com.waleed.naseej.dto.out.IdeaDetailResponse;
import com.waleed.naseej.entity.Address;
import com.waleed.naseej.entity.BusinessModel;
import com.waleed.naseej.entity.FoundingMember;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.IdeaCheck;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ExternalServiceException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.FoundingMemberRepository;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NearbyPlaceRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Nothing goes public without the author's say-so: a passing idea waits as a draft until "Publish". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftPublishTest {

    @Mock UserRepository userRepository;
    @Mock IdeaRepository ideaRepository;
    @Mock VoteRepository voteRepository;
    @Mock NeighborhoodRepository neighborhoodRepository;
    @Mock FoundingMemberRepository foundingMemberRepository;
    @Mock IdeaCheckRepository ideaCheckRepository;
    @Mock NearbyPlaceRepository nearbyPlaceRepository;
    @Mock BusinessModelRepository businessModelRepository;
    @Mock FeasibilityReportRepository feasibilityReportRepository;
    @Mock OpportunityAccessRepository opportunityAccessRepository;
    @Mock IdeaCheckService ideaCheckService;
    @Mock BusinessModelService businessModelService;
    @Mock FeasibilityReportService feasibilityReportService;
    @Mock NearbyPlaceService nearbyPlaceService;
    @Mock LocationService locationService;
    @Mock ProjectService projectService;
    @Mock AddressService addressService;
    @Mock AiTranslationService aiTranslationService;
    @Mock NotificationService notificationService;
    @InjectMocks DiscoverService discoverService;

    private User author;
    private User neighbor;
    private Neighborhood hood;
    private Idea idea;

    @BeforeEach
    void setUp() {
        hood = Neighborhood.builder().id(1).name("Al Rawdah").city("Riyadh").latitude(24.7300).longitude(46.7700).build();
        author = User.builder().id(3).role(Role.RESIDENT).build();
        neighbor = User.builder().id(4).role(Role.RESIDENT).build();
        idea = Idea.builder().id(9).owner(author).neighborhood(hood).status(IdeaStatus.SCREENING)
                .title("Late-night pharmacy").description("Nothing open after midnight").category("Pharmacy")
                .latitude(24.7301).longitude(46.7701).build();
        when(userRepository.findById(3)).thenReturn(Optional.of(author));
        when(userRepository.findById(4)).thenReturn(Optional.of(neighbor));
        when(ideaRepository.findWithNeighborhoodById(9)).thenReturn(Optional.of(idea));
        when(ideaRepository.save(any(Idea.class))).thenAnswer(inv -> {
            Idea saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(9);
            return saved;
        });
        when(foundingMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Address home = Address.builder().id(20).user(author).latitude(24.7300).longitude(46.7700).neighborhood(hood)
                .primaryAddress(true).needsConfirmation(false).build();
        when(addressService.addressesOf(author)).thenReturn(List.of(home));
        when(locationService.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn(Optional.of(hood));
        when(projectService.stageOfIdea(any())).thenReturn(Optional.empty());
    }

    private ProposeIdeaRequest request(String title) {
        ProposeIdeaRequest r = new ProposeIdeaRequest();
        r.setTitle(title);
        r.setDescription("Nothing open after midnight");
        r.setCategory("Pharmacy");
        r.setLatitude(24.7301);
        r.setLongitude(46.7701);
        return r;
    }

    private IdeaCheckResponse checkResult(String decision) {
        return IdeaCheckResponse.builder().ideaId(9).decision(decision).score(78).competitorsCount(0)
                .searchRadiusKm(2.0).scarcityFound(!"SATURATED".equals(decision)).build();
    }

    private void storedCheck(String decision, LocalDateTime checkedAt) {
        IdeaCheck check = IdeaCheck.builder().id(30).idea(idea).decision(decision).score(78).searchRadiusKm(2.0)
                .competitorsCount(0).scarcityFound(true).checkedAt(checkedAt).build();
        when(ideaCheckRepository.findByIdeaId(9)).thenReturn(Optional.of(check));
    }

    @Test
    void aPassingIdeaWaitsAsADraftWithItsBusinessModel() {
        when(ideaCheckService.run(9)).thenReturn(checkResult("FIT"));

        IdeaDetailResponse out = discoverService.propose(author, request("Late-night pharmacy"));

        assertEquals(IdeaStatus.DRAFT, idea.getStatus());
        assertNull(idea.getPublishedAt());
        assertEquals("DRAFT", out.getStatus());
        verify(businessModelService).generate(9);
    }

    @Test
    void aDraftIsHiddenFromOtherResidents() {
        idea.setStatus(IdeaStatus.DRAFT);
        assertThrows(ResourceNotFoundException.class, () -> discoverService.detail(neighbor, 9, null, null));
    }

    @Test
    void publishingAFreshDraftPutsItOnTheMap() {
        idea.setStatus(IdeaStatus.DRAFT);
        storedCheck("FIT", LocalDateTime.now().minusHours(2));
        when(businessModelRepository.existsByIdeaId(9)).thenReturn(true);

        discoverService.publish(author, 9);

        assertEquals(IdeaStatus.PUBLISHED, idea.getStatus());
        assertNotNull(idea.getPublishedAt());
    }

    @Test
    void aCheckOlderThanADayMustBeRunAgainBeforePublishing() {
        idea.setStatus(IdeaStatus.DRAFT);
        storedCheck("FIT", LocalDateTime.now().minusHours(30));

        assertThrows(BadRequestException.class, () -> discoverService.publish(author, 9));
        assertEquals(IdeaStatus.DRAFT, idea.getStatus());
    }

    @Test
    void anIdeaThatDidntPassCantBePublished() {
        idea.setStatus(IdeaStatus.REJECTED);
        storedCheck("SIMILAR", LocalDateTime.now());

        assertThrows(BadRequestException.class, () -> discoverService.publish(author, 9));
    }

    @Test
    void onlyTheAuthorCanPublish() {
        idea.setStatus(IdeaStatus.DRAFT);
        storedCheck("FIT", LocalDateTime.now());

        assertThrows(ForbiddenException.class, () -> discoverService.publish(neighbor, 9));
        assertEquals(IdeaStatus.DRAFT, idea.getStatus());
    }

    @Test
    void afterThreeRechecksAChangedIdeaIsRefusedWithoutCallingTheAi() {
        idea.setStatus(IdeaStatus.DRAFT);
        idea.setRecheckCount(DiscoverService.MAX_RECHECKS);

        assertThrows(BadRequestException.class, () -> discoverService.updateDraft(author, 9, request("24-hour pharmacy")));
        verify(ideaCheckService, never()).run(any());
        assertEquals("Late-night pharmacy", idea.getTitle());
    }

    @Test
    void checkingTheSameTextAgainDoesntUseARecheck() {
        idea.setStatus(IdeaStatus.DRAFT);
        idea.setRecheckCount(DiscoverService.MAX_RECHECKS);
        when(ideaCheckService.run(9)).thenReturn(checkResult("FIT"));

        discoverService.updateDraft(author, 9, request("Late-night pharmacy"));

        assertEquals(DiscoverService.MAX_RECHECKS, idea.getRecheckCount());
        assertEquals(IdeaStatus.DRAFT, idea.getStatus());
    }

    @Test
    void anEditCountsOneRecheckAndGetsANewBusinessModel() {
        idea.setStatus(IdeaStatus.DRAFT);
        idea.setRecheckCount(1);
        BusinessModel old = BusinessModel.builder().id(40).idea(idea).build();
        when(businessModelRepository.findByIdeaId(9)).thenReturn(Optional.of(old));
        when(ideaCheckService.run(9)).thenReturn(checkResult("FIT"));

        discoverService.updateDraft(author, 9, request("24-hour pharmacy"));

        assertEquals("24-hour pharmacy", idea.getTitle());
        assertEquals(2, idea.getRecheckCount());
        verify(businessModelRepository).delete(old);
        verify(businessModelService).generate(9);
    }

    @Test
    void whenTheCheckFailsTheEditIsUndone() {
        idea.setStatus(IdeaStatus.DRAFT);
        when(ideaCheckService.run(9)).thenThrow(new ExternalServiceException("The AI is busy"));

        assertThrows(ExternalServiceException.class, () -> discoverService.updateDraft(author, 9, request("24-hour pharmacy")));
        assertEquals("Late-night pharmacy", idea.getTitle());
        assertEquals(IdeaStatus.DRAFT, idea.getStatus());
    }

    @Test
    void cancellingADraftDeletesItAndWhatWasMadeForIt() {
        idea.setStatus(IdeaStatus.DRAFT);
        storedCheck("FIT", LocalDateTime.now());

        discoverService.cancelDraft(author, 9);

        verify(nearbyPlaceRepository).deleteAllForIdea(9);
        verify(foundingMemberRepository).deleteByIdeaId(9);
        verify(ideaCheckRepository).delete(any(IdeaCheck.class));
        verify(ideaRepository).delete(idea);
    }

    @Test
    void aPublishedIdeaCantBeCancelled() {
        idea.setStatus(IdeaStatus.PUBLISHED);

        assertThrows(BadRequestException.class, () -> discoverService.cancelDraft(author, 9));
        verify(ideaRepository, never()).delete(any(Idea.class));
    }

    @Test
    void anUnusedFoundingMemberRecordIsCleanedUpWhenTheFirstCheckFails() {
        when(ideaCheckService.run(9)).thenThrow(new ExternalServiceException("The AI is busy"));

        assertThrows(ExternalServiceException.class, () -> discoverService.propose(author, request("Late-night pharmacy")));
        verify(foundingMemberRepository).delete(any(FoundingMember.class));
        verify(ideaRepository).delete(any(Idea.class));
    }
}
