-- Flyway Migration: Create Alert_Email_Configs table
-- Matches com.grabit.cba.VendingMachineAlertService.database.model.AlertEmailConfig

CREATE TABLE IF NOT EXISTS Alert_Email_Configs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  alertTypeId INT NOT NULL,
  partnerId INT,
  `to` VARCHAR(1000),
  cc VARCHAR(1000),
  bcc VARCHAR(1000),
  createdAt DATETIME(6) NOT NULL,
  updatedAt DATETIME(6) NULL,
  CONSTRAINT fk_alert_email_config_alert_type FOREIGN KEY (alertTypeId) REFERENCES Alert_Type(id),
  CONSTRAINT fk_alert_email_config_partner FOREIGN KEY (partnerId) REFERENCES Partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
