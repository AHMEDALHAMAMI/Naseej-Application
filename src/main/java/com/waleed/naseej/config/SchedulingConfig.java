package com.waleed.naseej.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs, e.g. the nightly 90-day contract check (ContractExpiryJob). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
