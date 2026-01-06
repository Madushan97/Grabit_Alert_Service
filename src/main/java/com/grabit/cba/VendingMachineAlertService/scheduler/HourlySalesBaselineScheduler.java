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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HourlySalesBaselineScheduler {

    @Value("${monitor.lookback-period-months}")
    private Integer lookbackPeriodsMonths;

    private static final Logger LOGGER = LoggerFactory.getLogger(HourlySalesBaselineScheduler.class);

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

    // run once a day to compute baselines
    @Scheduled(cron = "${monitor.baselineCron:0 30 2 * * *}")
//    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void computeBaseline() {
        LOGGER.info("Hourly baseline job start for partner CBL");
        List<Partners> partners = partnersRepository.findByName("CBL");
        if (partners.isEmpty()) {
            LOGGER.warn("No partners with name CBL found");
            return;
        }
        List<Integer> partnerIds = partners.stream().map(Partners::getId).collect(Collectors.toList());

        List<Integer> merchantIds = merchantsRepository.findIdsByPartnerIds(partnerIds);
        if (merchantIds == null || merchantIds.isEmpty()) {
            LOGGER.warn("No merchants found for partner CBL (partnerIds={})", partnerIds);
            return;
        }
        List<VendingMachine> vms = vmRepository.findActiveByMerchantIds(merchantIds);

        LOGGER.info("Resolved partnerIds={} merchantIds={} vmsFound={}", partnerIds, merchantIds, vms == null ? 0 : vms.size());

        if (vms == null || vms.isEmpty()) {
            LOGGER.warn("No VMs for partner CBL");
            return;
        }

        // Defensive filtering: only keep VMs whose merchantId is in the resolved merchantIds
        List<VendingMachine> filteredVms = new ArrayList<>();
        for (VendingMachine vm : vms) {
            Integer mid = vm.getMerchantId();
            LOGGER.info("VM found id={} serial={} merchantId={}", vm.getId(), vm.getSerialNo(), mid);
            if (mid == null) {
                LOGGER.warn("Skipping VM id={} serial={} because merchantId is null", vm.getId(), vm.getSerialNo());
                continue;
            }
            if (!merchantIds.contains(mid)) {
                LOGGER.warn("Skipping VM id={} serial={} because merchantId {} is not in resolved merchantIds {}", vm.getId(), vm.getSerialNo(), mid, merchantIds);
                continue;
            }
            filteredVms.add(vm);
        }
        vms = filteredVms;

        if (vms.isEmpty()) {
            LOGGER.warn("No VMs remain after merchantId filtering for partner CBL");
            return;
        }

        // Define period: last 3 months
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime start = end.minusMonths(lookbackPeriodsMonths);

        long days = ChronoUnit.DAYS.between(start.toLocalDate().atStartOfDay(), end.toLocalDate().atStartOfDay());
        if (days <= 0) days = 1;

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
                if (machineId == null || !vmRepository.findById(machineId).isPresent()) {
                    LOGGER.warn("Skipping baseline save: VM id is invalid or missing (vmId={}, serial={})", machineId, vm.getSerialNo());
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
            LOGGER.info("Saved baseline for machine {} (sales records={})", vm.getId(), sales.size());
        }

        LOGGER.info("Hourly baseline job end");
    }
}
