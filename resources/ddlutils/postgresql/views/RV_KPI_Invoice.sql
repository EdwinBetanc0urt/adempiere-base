CREATE OR REPLACE VIEW RV_KPI_Invoice
(RV_KPI_Invoice_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 C_Invoice_ID, DocumentNo, POReference, Description, IsSOTrx, DocStatus, IsApproved, IsPaid, IsInDispute,
 C_DocType_ID, C_DocTypeTarget_ID, DocBaseType, DocSubTypeSO, C_Order_ID, M_RMA_ID,
 SalesRep_ID, AD_OrgTrx_ID, C_Campaign_ID, C_Project_ID, C_Activity_ID,
 DateInvoiced, DateAcct, DateOrdered,
 C_BPartner_ID, C_BPartner_Location_ID, AD_User_ID, C_BP_Group_ID,
 C_BP_AccountType_ID, C_BP_SalesGroup_ID, C_BP_Segment_ID, C_BP_IndustryType_ID, C_SalesRegion_ID,
 C_Country_ID, C_Region_ID, Postal, City,
 C_Currency_ID, C_ConversionType_ID, PaymentRule, C_PaymentTerm_ID, M_PriceList_ID, IsPayScheduleValid, InvoiceCollectionType,
 C_Charge_ID, ChargeAmt, TotalLines, GrandTotal, Multiplier)
AS
SELECT i.C_Invoice_ID, i.AD_Client_ID, i.AD_Org_ID, i.IsActive, i.Created, i.Updated,
    i.C_Invoice_ID, i.DocumentNo, i.POReference, i.Description, i.IsSOTrx, i.DocStatus, i.IsApproved, i.IsPaid, i.IsInDispute,
    i.C_DocType_ID, i.C_DocTypeTarget_ID, d.DocBaseType, d.DocSubTypeSO, i.C_Order_ID, i.M_RMA_ID,
    i.SalesRep_ID, i.AD_OrgTrx_ID, i.C_Campaign_ID, i.C_Project_ID, i.C_Activity_ID,
    i.DateInvoiced, i.DateAcct, i.DateOrdered,
    i.C_BPartner_ID, i.C_BPartner_Location_ID, i.AD_User_ID, b.C_BP_Group_ID,
    b.C_BP_AccountType_ID, b.C_BP_SalesGroup_ID, b.C_BP_Segment_ID, b.C_BP_IndustryType_ID, i.C_SalesRegion_ID,
    loc.C_Country_ID, loc.C_Region_ID, loc.Postal, loc.City,
    i.C_Currency_ID, i.C_ConversionType_ID, i.PaymentRule, i.C_PaymentTerm_ID, i.M_PriceList_ID, i.IsPayScheduleValid, i.InvoiceCollectionType,
    i.C_Charge_ID,
    CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.ChargeAmt * -1 ELSE i.ChargeAmt END,
    CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.TotalLines * -1 ELSE i.TotalLines END,
    CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.GrandTotal * -1 ELSE i.GrandTotal END,
    CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN -1 ELSE 1 END
FROM C_Invoice i
    INNER JOIN C_DocType d ON (d.C_DocType_ID = i.C_DocType_ID)
    INNER JOIN C_BPartner b ON (b.C_BPartner_ID = i.C_BPartner_ID)
    INNER JOIN C_BPartner_Location bpl ON (bpl.C_BPartner_Location_ID = i.C_BPartner_Location_ID)
    INNER JOIN C_Location loc ON (loc.C_Location_ID = bpl.C_Location_ID);
