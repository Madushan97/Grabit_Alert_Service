package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.service.ConsecutiveVoidCompleteMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ConsecutiveVoidCompleteAlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsecutiveVoidCompleteAlertScheduler.class);

    private final ConsecutiveVoidCompleteMonitorService consecutiveVoidCompleteMonitorService;

    public ConsecutiveVoidCompleteAlertScheduler(ConsecutiveVoidCompleteMonitorService consecutiveVoidCompleteMonitorService) {
        this.consecutiveVoidCompleteMonitorService = consecutiveVoidCompleteMonitorService;
    }

    @Scheduled(cron = "${monitor.consecutive-void-complete.consecutiveVoidCompleteCron:0 */5 * * * *}")
    public void checkingConsecutiveVoidCompleteTransactions() {
        LOGGER.info("Consecutive void complete cron start: Consecutive Void Complete Monitor");
        consecutiveVoidCompleteMonitorService.evaluateAllMachines();
        LOGGER.info("Consecutive void complete cron end: Consecutive Void Complete Monitor");
    }
}
