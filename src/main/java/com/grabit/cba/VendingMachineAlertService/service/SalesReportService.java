package com.grabit.cba.VendingMachineAlertService.service;

import java.time.LocalDateTime;
import java.util.Map;

public interface SalesReportService {
    /**
     * Generate sales report for given partner name (e.g., "CBL") and windowStart/windowEnd.
     * Returns a map that can be passed to the email template.
     */
    Map<String, Object> generateReportForPartner(String partnerName, LocalDateTime windowStart, LocalDateTime windowEnd);
}

