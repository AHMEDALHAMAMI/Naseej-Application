package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.ExpiredContractsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night: contracts that passed their opening deadline without opening are terminated and the idea goes
 * back to the investors. Kept apart from ContractFlowService so the call goes through its transaction.
 * The admin (or n8n) can run the same check any time with POST /admin/contracts/expire-overdue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryJob {

    private final ContractFlowService contractFlowService;

    @Scheduled(cron = "${app.contracts.expiry-cron:0 30 2 * * *}", zone = "Asia/Riyadh")
    public void expireOverdueContracts() {
        try {
            ExpiredContractsResponse result = contractFlowService.expireOverdue();
            if (result.getExpiredCount() > 0) {
                log.info("Terminated {} contract(s) past their opening deadline: {}",
                        result.getExpiredCount(), result.getContractNumbers());
            }
        } catch (RuntimeException ex) {
            log.error("The contract deadline check failed: {}", ex.getMessage(), ex);
        }
    }
}
