package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Integer> {

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineId = :vmId AND ah.alertType = :alertType ORDER BY ah.lastSentAt DESC")
    Optional<AlertHistory> findLatestByVendingMachineAndAlertType(@Param("vmId") Integer vendingMachineId, @Param("alertType") AlertType alertType);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType = :alertType ORDER BY ah.lastSentAt DESC")
    Optional<AlertHistory> findLatestByVendingMachineSerialAndAlertType(@Param("serial") String vendingMachineSerial, @Param("alertType") AlertType alertType);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineId = :vmId AND ah.alertType.id = :alertTypeId ORDER BY ah.lastSentAt DESC")
    Optional<AlertHistory> findLatestByVendingMachineIdAndAlertTypeId(@Param("vmId") Integer vendingMachineId, @Param("alertTypeId") Integer alertTypeId);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType.id = :alertTypeId ORDER BY ah.lastSentAt DESC")
    Optional<AlertHistory> findLatestByVendingMachineSerialAndAlertTypeId(@Param("serial") String vendingMachineSerial, @Param("alertTypeId") Integer alertTypeId);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial ORDER BY ah.lastSentAt DESC")
    Optional<AlertHistory> findLatestByVendingMachineSerial(@Param("serial") String vendingMachineSerial);
}
