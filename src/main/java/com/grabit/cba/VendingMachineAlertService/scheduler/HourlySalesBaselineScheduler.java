package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline.Id;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import com.grabit.cba.VendingMachineAlertService.database.repository.AlertHourlySalesBaselineRepository;
import com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository;
import com.grabit.cba.VendingMachineAlertService.database.repository.SalesRepository;
import com.grabit.cba.VendingMachineAlertService.database.repository.VMRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class HourlySalesBaselineScheduler {

    @Value("${monitor.baseline.lookback-period-months}")
    private Integer lookbackPeriodsMonths;

    private static final Logger LOGGER = LoggerFactory.getLogger(HourlySalesBaselineScheduler.class);

    // Optional safety guard to prevent double execution on startup near cron time
    private static final Duration MIN_INTERVAL = Duration.ofHours(23);
    private LocalDateTime lastRunTime;

    private final PartnersRepository partnersRepository;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository;
    private final VMRepository vmRepository;
    private final SalesRepository salesRepository;
    private final AlertHourlySalesBaselineRepository baselineRepository;

    public HourlySalesBaselineScheduler(PartnersRepository partnersRepository,
                                       com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository,
                                       VMRepository vmRepository, SalesRepository salesRepository, AlertHourlySalesBaselineRepository baselineRepository) {
        this.partnersRepository = partnersRepository;
        this.merchantsRepository = merchantsRepository;
        this.vmRepository = vmRepository;
        this.salesRepository = salesRepository;
        this.baselineRepository = baselineRepository;
    }

    private synchronized boolean canRunNow() {
        if (lastRunTime == null) return true;
        return Duration.between(lastRunTime, LocalDateTime.now()).compareTo(MIN_INTERVAL) > 0;
    }

    private void runBaselineJob(String trigger) {
        LocalDateTime now = LocalDateTime.now();
        if (!canRunNow()) {
            LOGGER.warn("Skipping baseline job – already executed recently. trigger={} at {} lastRunTime={}", trigger, now, lastRunTime);
            return;
        }
        lastRunTime = now;

        LOGGER.info("Hourly baseline job triggered by {} at {}", trigger, now);

        // Fetch all partners (remove hardcoded filtering)
        List<Partners> partners = partnersRepository.findAll();
        if (partners.isEmpty()) {
            LOGGER.warn("No partners found in repository");
            return;
        }

        // Define period once: last N months
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime start = end.minusMonths(lookbackPeriodsMonths);
        long days = ChronoUnit.DAYS.between(start.toLocalDate().atStartOfDay(), end.toLocalDate().atStartOfDay());
        if (days <= 0) days = 1;

        // Iterate per partner and evaluate baseline per machine without changing baseline logic
        for (Partners partner : partners) {
            Integer partnerId = partner.getId();
            String partnerName = partner.getName();

            if (partnerId == null) {
                LOGGER.warn("Skipping partner with null id (name={})", partnerName);
                continue;
            }

            // Fetch merchant IDs for this partner (batch by partner to avoid N+1 beyond partner granularity)
            List<Integer> merchantIds = merchantsRepository.findIdsByPartnerIds(Collections.singletonList(partnerId));
            if (merchantIds == null || merchantIds.isEmpty()) {
                LOGGER.warn("No merchants found for partner {} (partnerId={})", partnerName, partnerId);
                continue; // Skip partner
            }

            // Fetch active VMs for these merchants
            List<VendingMachine> vms = vmRepository.findActiveByMerchantIds(merchantIds);
            LOGGER.info("Partner={} (id={}) resolved merchantIds={} vmsFound={}", partnerName, partnerId, merchantIds, vms == null ? 0 : vms.size());

            if (vms == null || vms.isEmpty()) {
                LOGGER.warn("No active VMs for partner {} (partnerId={})", partnerName, partnerId);
                continue; // Skip partner
            }

            // Defensive filtering: only keep VMs whose merchantId is in the resolved merchantIds
            Set<Integer> merchantIdSet = new HashSet<>(merchantIds);
            List<VendingMachine> filteredVms = new ArrayList<>();
            for (VendingMachine vm : vms) {
                Integer mid = vm.getMerchantId();
                LOGGER.info("Partner={} VM found id={} serial={} merchantId={}", partnerName, vm.getId(), vm.getSerialNo(), mid);
                if (mid == null) {
                    LOGGER.warn("Partner={} skipping VM id={} serial={} because merchantId is null", partnerName, vm.getId(), vm.getSerialNo());
                    continue;
                }
                if (!merchantIdSet.contains(mid)) {
                    LOGGER.warn("Partner={} skipping VM id={} serial={} because merchantId {} is not in resolved merchantIds {}", partnerName, vm.getId(), vm.getSerialNo(), mid, merchantIds);
                    continue;
                }
                filteredVms.add(vm);
            }
            vms = filteredVms;

            if (vms.isEmpty()) {
                LOGGER.warn("Partner={} no VMs remain after merchantId filtering", partnerName);
                continue; // Skip partner
            }

            // Process each VM individually: fetch all sales once per VM and build per-hour buckets
            for (VendingMachine vm : vms) {
                List<Sales> sales = salesRepository.findByMachineSerialAndDateBetween(vm.getSerialNo(), start, end);

                // initialize counters per hour
                long[] successCounts = new long[24];
                long[] failedCounts = new long[24];
                long[] voidCompletedCounts = new long[24];
                long[] voidFailedCounts = new long[24];

                for (Sales s : sales) {
                    if (s.getDateTime() == null) continue;
                    int h = s.getDateTime().getHour();
                    String status = s.getTransactionStatus() == null ? "" : s.getTransactionStatus().toUpperCase();
                    switch (status) {
                        case "SALE_COMPLETED": successCounts[h]++; break;
                        case "SALE_FAILED": failedCounts[h]++; break;
                        case "VOID_COMPLETE": voidCompletedCounts[h]++; break;
                        case "VOID_FAILED": voidFailedCounts[h]++; break;
                        default: break;
                    }
                }

                // Save baseline per hour for this machine
                for (int hour = 0; hour < 24; hour++) {

                    double vmAvgSuccess = (double) successCounts[hour] / days;
                    double vmAvgFailed = (double) failedCounts[hour] / days;
                    double vmAvgVoidCompleted = (double) voidCompletedCounts[hour] / days;
                    double vmAvgVoidFailed = (double) voidFailedCounts[hour] / days;

                    Integer machineId = vm.getId();
                    if (machineId == null || vmRepository.findById(machineId).isEmpty()) {
                        LOGGER.warn("Partner={} skipping baseline save: VM id is invalid or missing (vmId={}, serial={})", partnerName, machineId, vm.getSerialNo());
                        continue;
                    }
                    Id key = new Id(machineId, hour);
                    AlertHourlySalesBaseline baseline = new AlertHourlySalesBaseline();
                    baseline.setId(key);
                    baseline.setAvgSalesCompleted(vmAvgSuccess);
                    baseline.setAvgSalesFailed(vmAvgFailed);
                    baseline.setAvgVoidCompleted(vmAvgVoidCompleted);
                    baseline.setAvgVoidFailed(vmAvgVoidFailed);
                    baseline.setUpdatedAt(LocalDateTime.now());
                    baselineRepository.save(baseline);
                }
                LOGGER.info("Partner={} saved baseline for machine {} (sales records={})", partnerName, vm.getId(), sales.size());
            }
        }

        LOGGER.info("Hourly baseline job end at {}", LocalDateTime.now());
    }

    // run once a day to compute baselines
    @Scheduled(cron = "${monitor.baseline.baselineCron:0 30 2 * * *}")
    //    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void computeBaseline() {
        runBaselineJob("SCHEDULED_CRON");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runOnStartup() {
        runBaselineJob("APPLICATION_STARTUP");
    }
}
