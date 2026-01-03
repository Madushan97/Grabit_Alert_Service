package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertHourlySalesBaselineRepository extends JpaRepository<AlertHourlySalesBaseline, Id> {
}

