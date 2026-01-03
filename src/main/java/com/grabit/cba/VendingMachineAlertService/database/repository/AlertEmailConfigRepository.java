package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.AlertEmailConfig;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertEmailConfigRepository extends JpaRepository<AlertEmailConfig, Integer> {

    List<AlertEmailConfig> findByAlertType(AlertType alertType);

    Optional<AlertEmailConfig> findFirstByAlertType(AlertType alertType);
}

