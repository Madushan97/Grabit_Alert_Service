package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Integer> {

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineId = :vmId AND ah.alertType = :alertType ORDER BY ah.lastSentAt DESC, ah.id DESC")
    List<AlertHistory> findAllByVendingMachineAndAlertType(@Param("vmId") Integer vendingMachineId, @Param("alertType") AlertType alertType, Pageable pageable);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType = :alertType ORDER BY ah.lastSentAt DESC, ah.id DESC")
    List<AlertHistory> findAllByVendingMachineSerialAndAlertType(@Param("serial") String vendingMachineSerial, @Param("alertType") AlertType alertType, Pageable pageable);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineId = :vmId AND ah.alertType.id = :alertTypeId ORDER BY ah.lastSentAt DESC, ah.id DESC")
    List<AlertHistory> findAllByVendingMachineIdAndAlertTypeId(@Param("vmId") Integer vendingMachineId, @Param("alertTypeId") Integer alertTypeId, Pageable pageable);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType.id = :alertTypeId ORDER BY ah.lastSentAt DESC, ah.id DESC")
    List<AlertHistory> findAllByVendingMachineSerialAndAlertTypeId(@Param("serial") String vendingMachineSerial, @Param("alertTypeId") Integer alertTypeId, Pageable pageable);

    // Keep the original methods for backward compatibility but fix them properly
    default Optional<AlertHistory> findLatestByVendingMachineAndAlertType(Integer vendingMachineId, AlertType alertType) {
        List<AlertHistory> results = findAllByVendingMachineAndAlertType(vendingMachineId, alertType, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    default Optional<AlertHistory> findLatestByVendingMachineSerialAndAlertType(String vendingMachineSerial, AlertType alertType) {
        List<AlertHistory> results = findAllByVendingMachineSerialAndAlertType(vendingMachineSerial, alertType, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    default Optional<AlertHistory> findLatestByVendingMachineIdAndAlertTypeId(Integer vendingMachineId, Integer alertTypeId) {
        List<AlertHistory> results = findAllByVendingMachineIdAndAlertTypeId(vendingMachineId, alertTypeId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    default Optional<AlertHistory> findLatestByVendingMachineSerialAndAlertTypeId(String vendingMachineSerial, Integer alertTypeId) {
        List<AlertHistory> results = findAllByVendingMachineSerialAndAlertTypeId(vendingMachineSerial, alertTypeId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }


    // Transaction-specific methods for individual alert tracking
    @Query("SELECT ah FROM AlertHistory ah WHERE ah.transactionId = :transactionId AND ah.alertType.id = :alertTypeId ORDER BY ah.lastSentAt DESC, ah.id DESC")
    List<AlertHistory> findAllByTransactionIdAndAlertTypeId(@Param("transactionId") Integer transactionId, @Param("alertTypeId") Integer alertTypeId, Pageable pageable);

    // Default methods for transaction-specific queries

    default Optional<AlertHistory> findLatestByTransactionIdAndAlertTypeId(Integer transactionId, Integer alertTypeId) {
        List<AlertHistory> results = findAllByTransactionIdAndAlertTypeId(transactionId, alertTypeId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT COUNT(ah) FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType.id = :alertTypeId AND ah.transactionId IS NOT NULL")
    Long countTransactionAlertsBySerialAndAlertType(@Param("serial") String vendingMachineSerial, @Param("alertTypeId") Integer alertTypeId);

    @Query("SELECT ah FROM AlertHistory ah WHERE ah.vendingMachineSerial = :serial AND ah.alertType.id = :alertTypeId AND ah.transactionId IS NOT NULL ORDER BY ah.lastSentAt DESC, ah.id DESC")
    java.util.List<AlertHistory> findTransactionAlertsBySerialAndAlertType(@Param("serial") String vendingMachineSerial, @Param("alertTypeId") Integer alertTypeId);
}
