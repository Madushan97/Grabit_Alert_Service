package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.service.HeartbeatMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class VMHeartbeatMonitorScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VMHeartbeatMonitorScheduler.class);

    private final HeartbeatMonitorService heartbeatMonitorService;

    public VMHeartbeatMonitorScheduler(HeartbeatMonitorService heartbeatMonitorService) {
        this.heartbeatMonitorService = heartbeatMonitorService;
    }

    @Scheduled(cron = "${monitor.heartbeat.heartbeatMonitoringCron:0 */10 * * * *}")
    public void runHeartbeatMonitoring() {
        LOGGER.info("Vending Machine Heartbeat monitoring cron start: Checking offline vending machines");
        heartbeatMonitorService.evaluateAllMachines();
        LOGGER.info("Vending Machine Heartbeat monitoring cron end: Offline vending machine check completed");
    }
}
