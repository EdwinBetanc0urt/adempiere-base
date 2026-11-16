CREATE OR REPLACE VIEW RV_KPI_Payment
(RV_KPI_Payment_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 C_Payment_ID, DocumentNo, Description, DateTrx, DateAcct, IsReceipt, DocStatus, C_DocType_ID, TrxType, TenderType, CheckNo,
 IsApproved, IsReconciled, IsAllocated, IsPrepayment, IsOverUnderPayment, IsUnidentifiedPayment, IsDelivered, Posted, Processed,
 C_BankAccount_ID, C_Bank_ID, C_CashBook_ID, C_PaymentMethod_ID, C_POS_ID, C_PaymentBatch_ID,
 C_BPartner_ID, C_BP_Group_ID, C_BP_AccountType_ID, C_BP_SalesGroup_ID, C_BP_Segment_ID, C_BP_IndustryType_ID,
 C_Invoice_ID, C_Order_ID, C_Charge_ID, C_Project_ID, C_Campaign_ID, C_Activity_ID, AD_OrgTrx_ID,
 Ref_Payment_ID, RelatedPayment_ID, Reversal_ID, Deposit_ID, Withdrawal_ID, CollectingAgent_ID,
 C_Currency_ID, C_ConversionType_ID,
 PayAmt, DiscountAmt, WriteOffAmt, TaxAmt, OverUnderAmt, ChargeAmt, MultiplierAP, AllocatedAmt, AvailableAmt)
AS
SELECT p.C_Payment_ID, p.AD_Client_ID, p.AD_Org_ID, p.IsActive, p.Created, p.Updated,
    p.C_Payment_ID, p.DocumentNo, p.Description, p.DateTrx, p.DateAcct, p.IsReceipt, p.DocStatus, p.C_DocType_ID, p.TrxType, p.TenderType, p.CheckNo,
    p.IsApproved, p.IsReconciled, p.IsAllocated, p.IsPrepayment, p.IsOverUnderPayment, p.IsUnidentifiedPayment, p.IsDelivered, p.Posted, p.Processed,
    p.C_BankAccount_ID, p.C_Bank_ID, p.C_CashBook_ID, p.C_PaymentMethod_ID, p.C_POS_ID, p.C_PaymentBatch_ID,
    p.C_BPartner_ID, bp.C_BP_Group_ID, bp.C_BP_AccountType_ID, bp.C_BP_SalesGroup_ID, bp.C_BP_Segment_ID, bp.C_BP_IndustryType_ID,
    p.C_Invoice_ID, p.C_Order_ID, p.C_Charge_ID, p.C_Project_ID, p.C_Campaign_ID, p.C_Activity_ID, p.AD_OrgTrx_ID,
    p.Ref_Payment_ID, p.RelatedPayment_ID, p.Reversal_ID, p.Deposit_ID, p.Withdrawal_ID, p.CollectingAgent_ID,
    p.C_Currency_ID, p.C_ConversionType_ID,
    CASE p.IsReceipt WHEN 'Y' THEN p.PayAmt ELSE p.PayAmt * -1 END,
    CASE p.IsReceipt WHEN 'Y' THEN p.DiscountAmt ELSE p.DiscountAmt * -1 END,
    CASE p.IsReceipt WHEN 'Y' THEN p.WriteOffAmt ELSE p.WriteOffAmt * -1 END,
    CASE p.IsReceipt WHEN 'Y' THEN p.TaxAmt ELSE p.TaxAmt * -1 END,
    CASE p.IsReceipt WHEN 'Y' THEN p.OverUnderAmt ELSE p.OverUnderAmt * -1 END,
    p.ChargeAmt,
    CASE p.IsReceipt WHEN 'Y' THEN 1 ELSE -1 END,
    CASE WHEN p.C_Charge_ID > 0 THEN p.PayAmt ELSE ROUND(COALESCE(al.Allocated, 0), 2) END,
    CASE
        WHEN p.C_Charge_ID > 0 THEN 0
        WHEN (CASE p.IsReceipt WHEN 'Y' THEN p.PayAmt ELSE p.PayAmt * -1 END) - COALESCE(al.Allocated, 0) BETWEEN -0.00999 AND 0.00999 THEN 0
        ELSE ROUND((CASE p.IsReceipt WHEN 'Y' THEN p.PayAmt ELSE p.PayAmt * -1 END) - COALESCE(al.Allocated, 0), 2)
    END
FROM C_Payment p
    LEFT JOIN C_BPartner bp ON (bp.C_BPartner_ID = p.C_BPartner_ID)
    LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN h.C_Currency_ID = p.C_Currency_ID THEN line.Amount
                ELSE currencyConvert(line.Amount, h.C_Currency_ID, p.C_Currency_ID, h.DateTrx, NULL, h.AD_Client_ID, h.AD_Org_ID) END) AS Allocated
        FROM C_AllocationLine line
            INNER JOIN C_AllocationHdr h ON (h.C_AllocationHdr_ID = line.C_AllocationHdr_ID)
        WHERE line.C_Payment_ID = p.C_Payment_ID AND h.DocStatus IN ('CO', 'CL')
    ) al ON TRUE;
