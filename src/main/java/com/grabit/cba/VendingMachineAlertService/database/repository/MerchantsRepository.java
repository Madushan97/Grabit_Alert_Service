package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantsRepository extends JpaRepository<Merchants, Integer> {

    @Query("SELECT m.id FROM Merchants m WHERE m.partnerId IN :partnerIds AND (m.isDeleted IS NULL OR m.isDeleted = false)")
    List<Integer> findIdsByPartnerIds(@Param("partnerIds") List<Integer> partnerIds);
}
