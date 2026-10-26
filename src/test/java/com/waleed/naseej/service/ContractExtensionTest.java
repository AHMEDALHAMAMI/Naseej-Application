package com.waleed.naseej.service;

import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ProjectUpdate;
import com.waleed.naseej.enums.ContractStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one 60-day extension (ProjectService.extensionBlock): an active project that hasn't opened, in the last
 * 30 days before its deadline, with a progress update in the last 30 days, and only once.
 */
class ContractExtensionTest {

    private static Contract active(int daysToDeadline) {
        Contract contract = Contract.builder()
                .status(ContractStatus.ACTIVE)
                .openingDeadline(LocalDate.now().plusDays(daysToDeadline))
                .build();
        contract.setActivatedAt(LocalDateTime.now().minusDays(150));
        return contract;
    }

    private static List<ProjectUpdate> updatedDaysAgo(int days) {
        return List.of(ProjectUpdate.builder().stage("Fit-out").createdAt(LocalDateTime.now().minusDays(days)).build());
    }

    @Test
    void nearTheDeadlineWithRecentProgressItCanExtend() {
        assertNull(ProjectService.extensionBlock(active(20), updatedDaysAgo(5)));
        assertNull(ProjectService.extensionBlock(active(0), updatedDaysAgo(29)));
    }

    @Test
    void tooEarlyBeforeTheLast30Days() {
        String reason = ProjectService.extensionBlock(active(60), updatedDaysAgo(5));
        assertNotNull(reason);
        assertTrue(reason.startsWith("The extension opens in the last 30 days"));
    }

    @Test
    void noRecentProgressMeansNoExtension() {
        assertTrue(ProjectService.extensionBlock(active(20), updatedDaysAgo(40)).startsWith("Post a progress update first"));
        assertTrue(ProjectService.extensionBlock(active(20), List.of()).startsWith("Post a progress update first"));
    }

    @Test
    void onlyOnce() {
        Contract contract = active(20);
        contract.setExtendedAt(LocalDateTime.now().minusDays(10));
        assertNotNull(ProjectService.extensionBlock(contract, updatedDaysAgo(5)));
    }

    @Test
    void notForUnsignedOpenedOrLateProjects() {
        Contract draft = active(20);
        draft.setStatus(ContractStatus.DRAFT);
        assertNotNull(ProjectService.extensionBlock(draft, updatedDaysAgo(5)));

        Contract opened = active(20);
        opened.setOpenedAt(LocalDateTime.now());
        assertNotNull(ProjectService.extensionBlock(opened, updatedDaysAgo(5)));

        assertNotNull(ProjectService.extensionBlock(active(-1), updatedDaysAgo(5)));
    }
}
