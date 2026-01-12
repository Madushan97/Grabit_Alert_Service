package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.VoidFailedTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoidFailedTrackingRepository extends JpaRepository<VoidFailedTracking, Integer> {

    Optional<VoidFailedTracking> findByVendingMachineSerial(String vendingMachineSerial);
}
