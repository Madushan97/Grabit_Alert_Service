package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.service.TimeoutMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class TimeoutAlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutAlertScheduler.class);

    private final TimeoutMonitorService timeoutMonitorService;

    public TimeoutAlertScheduler(TimeoutMonitorService timeoutMonitorService) {
        this.timeoutMonitorService = timeoutMonitorService;
    }

    @Scheduled(cron = "${monitor.timeout-monitor.cron:0 */5 * * * *}")
    public void checkingTimeoutTransactions() {
        LOGGER.info("Timeout cron start: Timeout Monitor");
        timeoutMonitorService.evaluateAllMachines();
        LOGGER.info("Timeout cron end: Timeout Monitor");
    }
}
