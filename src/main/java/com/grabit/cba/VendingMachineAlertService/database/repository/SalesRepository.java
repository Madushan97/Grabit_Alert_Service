package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesRepository extends JpaRepository<Sales, Integer> {

    @Query("SELECT s FROM Sales s JOIN s.vendingMachine vm WHERE vm.serialNo = :serialNo ORDER BY s.dateTime DESC")
    List<Sales> findLatestByMachineSerial(@Param("serialNo") String serialNo, Pageable pageable);

    @Query("SELECT s FROM Sales s JOIN s.vendingMachine vm WHERE vm.serialNo = :serialNo AND s.dateTime BETWEEN :start AND :end ORDER BY s.dateTime ASC")
    List<Sales> findByMachineSerialAndDateBetween(@Param("serialNo") String serialNo, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
