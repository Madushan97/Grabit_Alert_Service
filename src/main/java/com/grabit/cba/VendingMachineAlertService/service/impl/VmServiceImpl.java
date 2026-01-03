//package com.grabit.cba.VendingMachineAlertService.service.impl;
//
//import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
//import com.grabit.cba.VendingMachineAlertService.database.repository.VMRepository;
//import com.grabit.cba.VendingMachineAlertService.dto.responseDto.VMResponse;
//import com.grabit.cba.VendingMachineAlertService.exception.NotFoundException;
//import com.grabit.cba.VendingMachineAlertService.service.VMService;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//@Service
//@RequiredArgsConstructor
//public class VmServiceImpl implements VMService {
//
//    Logger LOGGER = LoggerFactory.getLogger(VmServiceImpl.class);
//
//    private final VMRepository vmRepository;
//
//    @Override
//    public VMResponse getVMService(Integer id) {
//
//        VendingMachine vm = vmRepository.findById(id).orElseThrow(() -> new NotFoundException("VM Not Found with ID: " + id));
//
//        LOGGER.info("🎯 VM found: {}", vm.getSerialNo());
//        VMResponse vmResponse = new VMResponse();
//        vmResponse.setId(vm.getId());
//        vmResponse.setSerialNo(vm.getSerialNo());
//        vmResponse.setMerchantId(vm.getMerchantId());
//        vmResponse.setIsDeleted(vm.getIsDeleted());
//        vmResponse.setName(vm.getName());
//        vmResponse.setStatus(vm.getStatus());
//        vmResponse.setTerminateCode(vm.getTerminateCode());
//        vmResponse.setProductLockCount(vm.getProductLockCount());
//        vmResponse.setLongitude(vm.getLongitude());
//        vmResponse.setLatitude(vm.getLatitude());
//        vmResponse.setColor(vm.getColor());
//
//        LOGGER.info("🎯 VMResponse prepared for VM ID: {}", id);
//        LOGGER.info("🎯 VMResponse details: {}", vmResponse);
//        return vmResponse;
//
//    }
//}
