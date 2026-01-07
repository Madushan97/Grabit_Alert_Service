-- Flyway Migration: Create Alert_History table
-- Matches com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory

CREATE TABLE IF NOT EXISTS Alert_History (
  id INT AUTO_INCREMENT PRIMARY KEY,
  vendingMachineId INT NULL,
  vendingMachineSerial VARCHAR(255),
  lastSentAt DATETIME(6),
  alertTypeId INT NOT NULL,
  partnerName VARCHAR(255),
  createdAt DATETIME(6) NOT NULL,
  updatedAt DATETIME(6) NULL,
  CONSTRAINT fk_alert_history_alert_type FOREIGN KEY (alertTypeId) REFERENCES Alert_Type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
