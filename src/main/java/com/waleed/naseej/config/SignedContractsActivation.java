package com.waleed.naseej.config;

import com.waleed.naseej.service.ContractFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The idea owner no longer signs the contract: it becomes active once the investor signs. Contracts the investor
 * signed before that change were waiting for the owner; this activates them once, on startup.
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class SignedContractsActivation implements CommandLineRunner {

    private final ContractFlowService contractFlowService;

    @Override
    public void run(String... args) {
        try {
            int activated = contractFlowService.activateSignedContracts();
            if (activated > 0) {
                log.info("{} contract(s) signed by the investor are now active (the idea owner no longer signs)", activated);
            }
        } catch (RuntimeException e) {
            log.warn("Couldn't activate the contracts waiting for the idea owner's signature: {}", e.getMessage());
        }
    }
}
