CREATE TABLE IF NOT EXISTS Alert_Void_Failed_Tracking (
    id INT PRIMARY KEY AUTO_INCREMENT,
    vendingMachineSerial VARCHAR(255) NOT NULL UNIQUE,
    lastCheckedTransactionId INT,
    lastCheckedDatetime DATETIME,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_vending_machine_serial (vendingMachineSerial),
    INDEX idx_last_checked_datetime (lastCheckedDatetime)
    );

