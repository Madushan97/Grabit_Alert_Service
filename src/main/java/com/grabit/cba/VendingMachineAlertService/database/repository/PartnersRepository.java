package com.grabit.cba.VendingMachineAlertService.database.repository;

import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnersRepository extends JpaRepository<Partners, Integer> {
    List<Partners> findByName(String name);
}

