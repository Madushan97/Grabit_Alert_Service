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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class MedianBasedHourlySalesBaselineScheduler {

    @Value("${monitor.baseline.lookback-period-months}")
    private Integer lookbackPeriodsMonths;

    private static final Logger LOGGER = LoggerFactory.getLogger(MedianBasedHourlySalesBaselineScheduler.class);

    // Optional safety guard to prevent double execution on startup near cron time
    private static final Duration MIN_INTERVAL = Duration.ofHours(23);
    private LocalDateTime lastRunTime;

    private final PartnersRepository partnersRepository;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository;
    private final VMRepository vmRepository;
    private final SalesRepository salesRepository;
    private final AlertHourlySalesBaselineRepository baselineRepository;


//    @Scheduled(cron = "${monitor.baseline.baselineCron:0 30 2 * * *}")
    @Transactional
    public void computeBaseline() {
        runBaselineJob("SCHEDULED_CRON");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runOnStartup() {
        runBaselineJob("APPLICATION_STARTUP");
    }

    public MedianBasedHourlySalesBaselineScheduler(PartnersRepository partnersRepository,
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

                // Build daily counters per hour (to calculate median instead of average)
                Map<LocalDate, long[]> dailySuccessCounts = new HashMap<>();
                Map<LocalDate, long[]> dailyFailedCounts = new HashMap<>();
                Map<LocalDate, long[]> dailyVoidCompletedCounts = new HashMap<>();
                Map<LocalDate, long[]> dailyVoidFailedCounts = new HashMap<>();

                for (Sales s : sales) {
                    if (s.getDateTime() == null) continue;
                    LocalDate day = s.getDateTime().toLocalDate();
                    int h = s.getDateTime().getHour();
                    String status = s.getTransactionStatus() == null ? "" : s.getTransactionStatus().toUpperCase();

                    // Initialize arrays if needed
                    dailySuccessCounts.computeIfAbsent(day, k -> new long[24]);
                    dailyFailedCounts.computeIfAbsent(day, k -> new long[24]);
                    dailyVoidCompletedCounts.computeIfAbsent(day, k -> new long[24]);
                    dailyVoidFailedCounts.computeIfAbsent(day, k -> new long[24]);

                    switch (status) {
                        case "SALE_COMPLETED": dailySuccessCounts.get(day)[h]++; break;
                        case "SALE_FAILED": dailyFailedCounts.get(day)[h]++; break;
                        case "VOID_COMPLETE": dailyVoidCompletedCounts.get(day)[h]++; break;
                        case "VOID_FAILED": dailyVoidFailedCounts.get(day)[h]++; break;
                        default: break;
                    }
                }

                // Save baseline per hour for this machine
                for (int hour = 0; hour < 24; hour++) {

                    double vmMedianSuccess = calculateMedian(dailySuccessCounts, hour);
                    double vmMedianFailed = calculateMedian(dailyFailedCounts, hour);
                    double vmMedianVoidCompleted = calculateMedian(dailyVoidCompletedCounts, hour);
                    double vmMedianVoidFailed = calculateMedian(dailyVoidFailedCounts, hour);

                    Integer machineId = vm.getId();
                    if (machineId == null) {
                        LOGGER.warn("Partner={} skipping baseline save: VM id is invalid or missing (vmId={}, serial={})", partnerName, machineId, vm.getSerialNo());
                        continue;
                    }
                    Id key = new Id(machineId, hour);
                    AlertHourlySalesBaseline baseline = new AlertHourlySalesBaseline();
                    baseline.setId(key);
                    baseline.setMedianSalesCompleted(vmMedianSuccess);
                    baseline.setMedianSalesFailed(vmMedianFailed);
                    baseline.setMedianVoidCompleted(vmMedianVoidCompleted);
                    baseline.setMedianVoidFailed(vmMedianVoidFailed);
                    baseline.setUpdatedAt(LocalDateTime.now());
                    baselineRepository.save(baseline);
                }
                LOGGER.info("Partner={} saved baseline for machine {} (sales records={})", partnerName, vm.getId(), sales.size());
            }
        }

        LOGGER.info("Hourly baseline job end at {}", LocalDateTime.now());
    }


    /**
     * Calculate median from daily counts for a specific hour.
     * This is more robust against outliers than average.
     *
     * @param dailyCounts Map of date -> daily count array [24 hours]
     * @param hour The hour of day (0-23) to calculate median for
     * @return median value for the given hour across all days
     */
    private double calculateMedian(Map<LocalDate, long[]> dailyCounts, int hour) {
        List<Long> values = new ArrayList<>();

        // Extract all daily counts for this specific hour
        for (long[] dailyHourCounts : dailyCounts.values()) {
            values.add(dailyHourCounts[hour]);
        }

        if (values.isEmpty()) {
            return 0.0;
        }

        // Sort values to find median
        Collections.sort(values);
        int size = values.size();

        if (size % 2 == 0) {
            // Even number of values: average of two middle values
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            // Odd number of values: middle value
            return values.get(size / 2);
        }
    }
}
