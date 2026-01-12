-- Flyway Migration: Create Alert_History table
-- Matches com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory

-- CREATE TABLE IF NOT EXISTS Alert_History (
--   id INT AUTO_INCREMENT PRIMARY KEY,
--   vendingMachineId INT NULL,
--   vendingMachineSerial VARCHAR(255),
--   lastSentAt DATETIME(6),
--   alertTypeId INT NOT NULL,
--   partnerName VARCHAR(255),
--   createdAt DATETIME(6) NOT NULL,
--   updatedAt DATETIME(6) NULL,
--   CONSTRAINT fk_alert_history_alert_type FOREIGN KEY (alertTypeId) REFERENCES Alert_Type(id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Flyway Migration: Create Alert_History table
-- Supports machine-level and transaction-level alerting (e.g., VOID_FAILED)

CREATE TABLE IF NOT EXISTS Alert_History (
id INT AUTO_INCREMENT PRIMARY KEY,

    -- Machine identification
    vendingMachineId INT NULL,
    vendingMachineSerial VARCHAR(255) NULL,

    -- Transaction-level alert support
    transactionId INT NULL COMMENT 'Reference to Sales.id for individual transaction alerts',

    -- Alert metadata
    alertTypeId INT NOT NULL,
    partnerName VARCHAR(255) NULL,
    lastSentAt DATETIME(6) NULL,

    -- Audit fields
    createdAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updatedAt DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Foreign keys
    CONSTRAINT fk_alert_history_alert_type
    FOREIGN KEY (alertTypeId) REFERENCES Alert_Type(id),

    -- Indexes for performance
    INDEX idx_alert_history_transaction_id (transactionId),
    INDEX idx_alert_history_transaction_alert_type (transactionId, alertTypeId),
    INDEX idx_alert_history_serial_transaction (vendingMachineSerial, transactionId)

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;
