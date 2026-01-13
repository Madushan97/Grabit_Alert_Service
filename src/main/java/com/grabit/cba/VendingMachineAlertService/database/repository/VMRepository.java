package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VMRepository extends JpaRepository<VendingMachine,Integer> {
    Optional<VendingMachine> findBySerialNo(String serialNo);

    @Query("SELECT vm FROM VendingMachine vm WHERE vm.merchantId IN :merchantIds AND (vm.isDeleted IS NULL OR vm.isDeleted = false) AND vm.status = 1")
    List<VendingMachine> findActiveByMerchantIds(@Param("merchantIds") List<Integer> merchantIds);

    @Query("SELECT vm FROM VendingMachine vm WHERE vm.merchantId IN :merchantIds AND (vm.isDeleted IS NULL OR vm.isDeleted = false) AND vm.status = 0")
    List<VendingMachine> findOfflineByMerchantIds(@Param("merchantIds") List<Integer> merchantIds);
}
