package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.service.VoidFailedHealthMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class VoidFailedAlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoidFailedAlertScheduler.class);

    private final VoidFailedHealthMonitorService voidFailedHealthMonitorService;

    public VoidFailedAlertScheduler(VoidFailedHealthMonitorService voidFailedHealthMonitorService) {
        this.voidFailedHealthMonitorService = voidFailedHealthMonitorService;
    }

    @Scheduled(cron = "${monitor.void-failed.voidFailedCron:0 */5 * * * *}")
    public void checkingVoidFailedTransactions() {
        LOGGER.info("Void failed Cron start: Void Failed Health Monitor");
        voidFailedHealthMonitorService.evaluateAllMachines();
        LOGGER.info("Void failed Cron end: Void Failed Health Monitor");
    }
}
