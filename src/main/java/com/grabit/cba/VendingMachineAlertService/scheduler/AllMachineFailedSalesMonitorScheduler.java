package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.config.AllMachinesMonitorProperties;
import com.grabit.cba.VendingMachineAlertService.service.AllMachineSaleFailedHealthMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class AllMachineFailedSalesMonitorScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllMachineFailedSalesMonitorScheduler.class);

    private final AllMachinesMonitorProperties allMachinesMonitorProperties;
    private final AllMachineSaleFailedHealthMonitorService allMachineSaleFailedHealthMonitorService;

    public AllMachineFailedSalesMonitorScheduler(AllMachinesMonitorProperties allMachinesMonitorProperties,
                                                 AllMachineSaleFailedHealthMonitorService allMachineSaleFailedHealthMonitorService) {
        this.allMachinesMonitorProperties = allMachinesMonitorProperties;
        this.allMachineSaleFailedHealthMonitorService = allMachineSaleFailedHealthMonitorService;
    }

    @Scheduled(cron = "${monitor.failed-sales.cron:0 */5 * * * *}")
    public void runCron() {
        LOGGER.info("Cron start: Machine health monitor");
        allMachineSaleFailedHealthMonitorService.evaluateAllMachines();
        LOGGER.info("Cron end: Machine health monitor");
    }
}

