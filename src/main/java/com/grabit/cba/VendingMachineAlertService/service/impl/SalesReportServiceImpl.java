package com.grabit.cba.VendingMachineAlertService.service.impl;

import com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository;
import com.grabit.cba.VendingMachineAlertService.database.repository.SalesRepository;
import com.grabit.cba.VendingMachineAlertService.database.repository.VMRepository;
import com.grabit.cba.VendingMachineAlertService.service.SalesReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesReportServiceImpl implements SalesReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalesReportServiceImpl.class);

    private final PartnersRepository partnersRepository;
    private final VMRepository vmRepository;
    private final SalesRepository salesRepository;

    public SalesReportServiceImpl(PartnersRepository partnersRepository, VMRepository vmRepository, SalesRepository salesRepository) {
        this.partnersRepository = partnersRepository;
        this.vmRepository = vmRepository;
        this.salesRepository = salesRepository;
    }

    @Override
    public Map<String, Object> generateReportForPartner(String partnerName, LocalDateTime windowStart, LocalDateTime windowEnd) {
        Map<String, Object> result = new HashMap<>();
        // For now only support partnerName = "CBL" as requested
        List<com.grabit.cba.VendingMachineAlertService.database.model.other.Partners> partners = partnersRepository.findByName(partnerName);
        if (partners.isEmpty()) {
            LOGGER.warn("No partners found for name {}", partnerName);
            return result;
        }
        // Collect VMs for these partners
        List<Integer> partnerIds = partners.stream().map(com.grabit.cba.VendingMachineAlertService.database.model.other.Partners::getId).collect(Collectors.toList());
        List<com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine> vms = vmRepository.findAll().stream()
                .filter(vm -> vm.getMerchantId() != null && partnerIds.contains(vm.getMerchantId()))
                .filter(vm -> Boolean.TRUE.equals(vm.getIsDeleted()) == false)
                .collect(Collectors.toList());

        List<Map<String, Object>> vmReports = new ArrayList<>();
        int totalCompleted = 0;
        int totalFailed = 0;
        int totalVoidCompleted = 0;
        int totalVoidFailed = 0;

        for (com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine vm : vms) {
            String serial = vm.getSerialNo();
            List<com.grabit.cba.VendingMachineAlertService.database.model.other.Sales> sales = salesRepository.findLatestByMachineSerial(serial, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
            // filter by window
            List<com.grabit.cba.VendingMachineAlertService.database.model.other.Sales> filtered = sales.stream()
                    .filter(s -> s.getDateTime() != null && !s.getDateTime().isBefore(windowStart) && !s.getDateTime().isAfter(windowEnd))
                    .collect(Collectors.toList());
            long completed = filtered.stream().filter(s -> "SUCCESS".equalsIgnoreCase(s.getTransactionStatus())).count();
            long failed = filtered.stream().filter(s -> "SALE_FAILED".equalsIgnoreCase(s.getTransactionStatus())).count();
            long voidCompleted = filtered.stream().filter(s -> "VOID_COMPLETE".equalsIgnoreCase(s.getTransactionStatus())).count();
            long voidFailed = filtered.stream().filter(s -> "VOID_FAILED".equalsIgnoreCase(s.getTransactionStatus())).count();

            totalCompleted += completed;
            totalFailed += failed;
            totalVoidCompleted += voidCompleted;
            totalVoidFailed += voidFailed;

            Map<String, Object> vmMap = new HashMap<>();
            vmMap.put("serial", serial);
            vmMap.put("name", vm.getName());
            vmMap.put("completed", completed);
            vmMap.put("failed", failed);
            vmMap.put("voidCompleted", voidCompleted);
            vmMap.put("voidFailed", voidFailed);
            vmReports.add(vmMap);
        }

        result.put("partnerName", partnerName);
        result.put("windowStart", windowStart);
        result.put("windowEnd", windowEnd);
        result.put("vms", vmReports);
        result.put("totalCompleted", totalCompleted);
        result.put("totalFailed", totalFailed);
        result.put("totalVoidCompleted", totalVoidCompleted);
        result.put("totalVoidFailed", totalVoidFailed);

        return result;
    }
}

