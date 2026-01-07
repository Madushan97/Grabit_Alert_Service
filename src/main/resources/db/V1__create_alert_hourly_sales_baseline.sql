-- Flyway Migration: Create Alert_Hourly_Sales_Baseline table
-- Matches com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline

CREATE TABLE IF NOT EXISTS Alert_Hourly_Sales_Baseline (
  machineId INT NOT NULL,
  hourOfDay INT NOT NULL,
  avgSalesCompleted DOUBLE,
  avgSalesFailed DOUBLE,
  avgVoidCompleted DOUBLE,
  avgVoidFailed DOUBLE,
  updated_at DATETIME(6),
  PRIMARY KEY (machineId, hourOfDay)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
