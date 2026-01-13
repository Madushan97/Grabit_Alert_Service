
CREATE TABLE IF NOT EXISTS Alert_Hourly_Sales_Baseline (
  machineId INT NOT NULL,
  hourOfDay INT NOT NULL,
  medianSalesCompleted DOUBLE,
  medianSalesFailed DOUBLE,
  medianVoidCompleted DOUBLE,
  medianVoidFailed DOUBLE,
  updated_at DATETIME(6),
  PRIMARY KEY (machineId, hourOfDay)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
