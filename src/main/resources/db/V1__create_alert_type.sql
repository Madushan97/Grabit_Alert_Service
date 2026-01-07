-- Flyway Migration: Create Alert_Type table
-- Matches com.grabit.cba.VendingMachineAlertService.database.model.AlertType

CREATE TABLE IF NOT EXISTS Alert_Type (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(500),
  severity VARCHAR(50) NOT NULL,
  createdAt DATETIME(6) NOT NULL,
  updatedAt DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
