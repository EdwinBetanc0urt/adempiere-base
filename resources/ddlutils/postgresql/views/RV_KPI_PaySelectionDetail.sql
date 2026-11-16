CREATE OR REPLACE VIEW RV_KPI_PaySelectionDetail
(RV_KPI_PaySelectionDetail_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 C_PaySelection_ID, C_PaySelectionLine_ID, DocumentNo, Name, Description, LineDescription, DocStatus, IsApproved, Processed,
 C_DocType_ID, C_BankAccount_ID, C_Currency_ID, C_ConversionType_ID, C_Conversion_Rate_ID,
 PayDate, DateDoc, Line, IsManual, IsSOTrx, IsPrepayment, PaymentRule,
 C_BankAccountTo_ID, C_BP_BankAccount_ID, C_BPartner_ID, C_BP_Group_ID, C_Charge_ID, HR_Movement_ID,
 C_Invoice_ID, C_InvoicePaySchedule_ID, C_Order_ID, C_PaySelectionCheck_ID, C_Payment_ID,
 TotalAmt, AmtSource, OpenAmt, DiscountAmt, PayAmt, DifferenceAmt, Qty)
AS
SELECT psl.C_PaySelectionLine_ID, ps.AD_Client_ID, ps.AD_Org_ID, ps.IsActive, ps.Created, ps.Updated,
    ps.C_PaySelection_ID, psl.C_PaySelectionLine_ID, ps.DocumentNo, ps.Name, ps.Description, psl.Description, ps.DocStatus, ps.IsApproved, ps.Processed,
    ps.C_DocType_ID, ps.C_BankAccount_ID, ps.C_Currency_ID, psl.C_ConversionType_ID, psl.C_Conversion_Rate_ID,
    ps.PayDate, ps.DateDoc, psl.Line, psl.IsManual, psl.IsSOTrx, psl.IsPrepayment, psl.PaymentRule,
    psl.C_BankAccountTo_ID, psl.C_BP_BankAccount_ID, psl.C_BPartner_ID, bp.C_BP_Group_ID, psl.C_Charge_ID, psl.HR_Movement_ID,
    psl.C_Invoice_ID, psl.C_InvoicePaySchedule_ID, psl.C_Order_ID, psl.C_PaySelectionCheck_ID, psc.C_Payment_ID,
    ps.TotalAmt, psl.AmtSource, psl.OpenAmt, psl.DiscountAmt, psl.PayAmt, psl.DifferenceAmt, psc.Qty
FROM C_PaySelection ps
    INNER JOIN C_PaySelectionLine psl ON (psl.C_PaySelection_ID = ps.C_PaySelection_ID)
    LEFT JOIN C_PaySelectionCheck psc ON (psc.C_PaySelectionCheck_ID = psl.C_PaySelectionCheck_ID)
    LEFT JOIN C_BPartner bp ON (bp.C_BPartner_ID = psl.C_BPartner_ID);
