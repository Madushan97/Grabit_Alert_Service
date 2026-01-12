CREATE TABLE IF NOT EXISTS Alert_Void_Failed_Tracking (
    id INT PRIMARY KEY AUTO_INCREMENT,
    vending_machine_serial VARCHAR(255) NOT NULL UNIQUE,
    last_checked_transaction_id INT,
    last_checked_datetime DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_vending_machine_serial (vending_machine_serial),
    INDEX idx_last_checked_datetime (last_checked_datetime)
);

