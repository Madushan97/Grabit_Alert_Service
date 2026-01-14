package com.grabit.cba.VendingMachineAlertService.database.model.other;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Table(name = "Sales")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Immutable
@ToString(exclude = "vendingMachine")
public class Sales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "amount")
    private Integer amount;

    @Column(name = "dateTime")
    private LocalDateTime dateTime;

    // itemId column exists in DB and has a FK to items(id). There's no Item entity in this project,
    // so map it as a simple Integer. If you add an Item entity later, switch to @ManyToOne.
    @Column(name = "itemId")
    private Integer itemId;

    // Map the vending machine relationship as ManyToOne since there is an existing VendingMachine entity.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendingMachineId", foreignKey = @ForeignKey(name = "Sales_ibfk_12"))
    private VendingMachine vendingMachine;

    @Column(name = "transactionStatus")
    private String transactionStatus;

    // Keep the DB default in the column definition to reflect the CREATE TABLE
    @Column(name = "cretateAt", columnDefinition = "datetime DEFAULT '2024-06-18 15:33:27'", insertable = false, updatable = false)
    private LocalDateTime cretateAt;

    @Column(name = "TranInvoiceNo")
    private String tranInvoiceNo;

    @Column(name = "TranBatchNo")
    private String tranBatchNo;

    @Column(name = "TranTerminalID")
    private String tranTerminalID;

    @Column(name = "TranMerchantID")
    private String tranMerchantID;

    @Column(name = "TranHostName")
    private String tranHostName;

    @Column(name = "TranApproveCode")
    private String tranApproveCode;

    @Column(name = "TranResponseCode")
    private String tranResponseCode;

    @Column(name = "TranRRN")
    private String tranRRN;

    @Column(name = "TranMaskedPAN")
    private String tranMaskedPAN;

    @Column(name = "TranCardType")
    private String tranCardType;

    @Column(name = "TranExpDate")
    private String tranExpDate;

    @Column(name = "TranEntryMode")
    private String tranEntryMode;

    @Column(name = "TranRFU")
    private String tranRFU;

    @Column(name = "TranCardHolderName")
    private String tranCardHolderName;

    @Column(name = "TranTransactionAmount")
    private String tranTransactionAmount;

    @Column(name = "TranECRReferenceNo")
    private String tranECRReferenceNo;

    @Column(name = "TranBINRequestReferenceNo")
    private String tranBINRequestReferenceNo;

    @Column(name = "TranMobileNo")
    private String tranMobileNo;

    @Column(name = "TranStatusDescription")
    private String tranStatusDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "TranPaymentMode", nullable = false, columnDefinition = "enum('CARD','QR')")
    private TranPaymentMode tranPaymentMode;

    @Column(name = "discount")
    private Integer discount;

    @Column(name = "unit_price")
    private Integer unitPrice;

    public enum TranPaymentMode {
        CARD, QR
    }

}
