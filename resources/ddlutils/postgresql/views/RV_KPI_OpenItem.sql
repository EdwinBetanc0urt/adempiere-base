CREATE OR REPLACE VIEW RV_KPI_OpenItem
(RV_KPI_OpenItem_ID, AD_Client_ID, AD_Org_ID, IsActive,
 C_Invoice_ID, C_InvoicePaySchedule_ID, DocumentNo, POReference, Description, IsSOTrx, DocStatus, C_DocType_ID, DocBaseType, C_Order_ID,
 C_BPartner_ID, C_BPartner_Location_ID, AD_User_ID, C_BP_Group_ID,
 C_BP_AccountType_ID, C_BP_SalesGroup_ID, C_BP_Segment_ID, C_BP_IndustryType_ID,
 SalesRep_ID, C_SalesRegion_ID, AD_OrgTrx_ID, C_Campaign_ID, C_Project_ID, C_Activity_ID, M_PriceList_ID,
 DateInvoiced, DateAcct, DueDate, DiscountDate, NetDays, DaysDue,
 C_Currency_ID, C_ConversionType_ID, PaymentRule, C_PaymentTerm_ID, IsPayScheduleValid, InvoiceCollectionType, IsInDispute,
 GrandTotal, DiscountAmt, PaidAmt, OpenAmt)
AS
SELECT CASE WHEN x.C_InvoicePaySchedule_ID IS NULL THEN x.C_Invoice_ID ELSE x.C_InvoicePaySchedule_ID + 1000000000 END,
    x.AD_Client_ID, x.AD_Org_ID, x.IsActive,
    x.C_Invoice_ID, x.C_InvoicePaySchedule_ID, x.DocumentNo, x.POReference, x.Description, x.IsSOTrx, x.DocStatus, x.C_DocType_ID, x.DocBaseType, x.C_Order_ID,
    x.C_BPartner_ID, x.C_BPartner_Location_ID, x.AD_User_ID, x.C_BP_Group_ID,
    x.C_BP_AccountType_ID, x.C_BP_SalesGroup_ID, x.C_BP_Segment_ID, x.C_BP_IndustryType_ID,
    x.SalesRep_ID, x.C_SalesRegion_ID, x.AD_OrgTrx_ID, x.C_Campaign_ID, x.C_Project_ID, x.C_Activity_ID, x.M_PriceList_ID,
    x.DateInvoiced, x.DateAcct, x.DueDate, x.DiscountDate, x.NetDays, x.DaysDue,
    x.C_Currency_ID, x.C_ConversionType_ID, x.PaymentRule, x.C_PaymentTerm_ID, x.IsPayScheduleValid, x.InvoiceCollectionType, x.IsInDispute,
    x.GrandTotal, x.DiscountAmt, x.PaidAmt, x.OpenAmt
FROM (
    SELECT y.*,
        CASE WHEN y.Raw > -y.Eps AND y.Raw < y.Eps THEN 0 ELSE ROUND(COALESCE(y.Raw, 0), CAST(y.Prec AS INTEGER)) END AS OpenAmt
    FROM (
        SELECT b.*,
            CASE WHEN b.C_InvoicePaySchedule_ID IS NULL THEN b.SignedTotal - b.MultAP * b.PaidRaw
                 ELSE CASE WHEN b.DueAmt - GREATEST(0, b.MultAP * b.PaidRaw - b.CumDueBefore) < 0 THEN 0
                           ELSE b.DueAmt * b.MultCM - GREATEST(0, b.MultAP * b.PaidRaw - b.CumDueBefore) END
            END AS Raw
        FROM (
            SELECT i.AD_Client_ID, i.AD_Org_ID, i.IsActive, i.C_Invoice_ID, i.DocumentNo, i.POReference, i.Description, i.IsSOTrx, i.DocStatus,
                i.C_DocType_ID, d.DocBaseType, i.C_Order_ID,
                i.C_BPartner_ID, i.C_BPartner_Location_ID, i.AD_User_ID, bp.C_BP_Group_ID,
                bp.C_BP_AccountType_ID, bp.C_BP_SalesGroup_ID, bp.C_BP_Segment_ID, bp.C_BP_IndustryType_ID,
                i.SalesRep_ID, i.C_SalesRegion_ID, i.AD_OrgTrx_ID, i.C_Campaign_ID, i.C_Project_ID, i.C_Activity_ID, i.M_PriceList_ID,
                i.DateInvoiced, i.DateAcct, i.C_Currency_ID, i.C_ConversionType_ID, i.PaymentRule, i.C_PaymentTerm_ID,
                i.IsPayScheduleValid, i.InvoiceCollectionType, i.IsInDispute,
                CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN -1 ELSE 1 END AS MultCM,
                CASE WHEN SUBSTR(d.DocBaseType, 2, 1) = 'P' THEN -1 ELSE 1 END AS MultAP,
                CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.GrandTotal * -1 ELSE i.GrandTotal END AS SignedTotal,
                COALESCE(al.Paid, 0) AS PaidRaw,
                1 / POWER(10, cur.StdPrecision) AS Eps, cur.StdPrecision AS Prec,
                CAST(NULL AS NUMERIC) AS C_InvoicePaySchedule_ID, CAST(NULL AS NUMERIC) AS DueAmt, CAST(0 AS NUMERIC) AS CumDueBefore,
                p.NetDays,
                CASE WHEN p.IsDueFixed = 'Y' THEN paymenttermduedate(i.C_PaymentTerm_ID, i.DateInvoiced::timestamptz)
                     ELSE CAST(i.DateInvoiced AS DATE) + CAST(p.NetDays AS INTEGER) END AS DueDate,
                CASE WHEN p.IsDueFixed = 'Y' THEN paymenttermduedays(i.C_PaymentTerm_ID, i.DateInvoiced::timestamptz, now())
                     ELSE CAST(now() AS DATE) - (CAST(i.DateInvoiced AS DATE) + CAST(p.NetDays AS INTEGER)) END AS DaysDue,
                CAST(i.DateInvoiced AS DATE) + CAST(p.DiscountDays AS INTEGER) AS DiscountDate,
                ROUND(CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.GrandTotal * -1 ELSE i.GrandTotal END * p.Discount / 100, 2) AS DiscountAmt,
                CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN i.GrandTotal * -1 ELSE i.GrandTotal END AS GrandTotal,
                ROUND(COALESCE(al.Paid, 0), 2) AS PaidAmt
            FROM C_Invoice i
                INNER JOIN C_DocType d ON (d.C_DocType_ID = i.C_DocType_ID)
                INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = i.C_BPartner_ID)
                INNER JOIN C_BPartner_Location bpl ON (bpl.C_BPartner_Location_ID = i.C_BPartner_Location_ID)
                INNER JOIN C_Location loc ON (loc.C_Location_ID = bpl.C_Location_ID)
                INNER JOIN C_PaymentTerm p ON (p.C_PaymentTerm_ID = i.C_PaymentTerm_ID)
                INNER JOIN C_Currency cur ON (cur.C_Currency_ID = i.C_Currency_ID)
                LEFT JOIN LATERAL (
                    SELECT SUM(CASE WHEN h.C_Currency_ID = i.C_Currency_ID THEN line.Amount + line.DiscountAmt + line.WriteOffAmt
                            ELSE currencyConvert(line.Amount + line.DiscountAmt + line.WriteOffAmt, h.C_Currency_ID, i.C_Currency_ID, h.DateTrx, NULL, h.AD_Client_ID, h.AD_Org_ID) END) AS Paid
                    FROM C_AllocationLine line
                        INNER JOIN C_AllocationHdr h ON (h.C_AllocationHdr_ID = line.C_AllocationHdr_ID)
                    WHERE line.C_Invoice_ID = i.C_Invoice_ID AND h.DocStatus IN ('CO', 'CL')
                ) al ON TRUE
            WHERE i.DocStatus IN ('CO', 'CL') AND i.IsPayScheduleValid <> 'Y'
            UNION ALL
            SELECT i.AD_Client_ID, i.AD_Org_ID, i.IsActive, i.C_Invoice_ID, i.DocumentNo, i.POReference, i.Description, i.IsSOTrx, i.DocStatus,
                i.C_DocType_ID, d.DocBaseType, i.C_Order_ID,
                i.C_BPartner_ID, i.C_BPartner_Location_ID, i.AD_User_ID, bp.C_BP_Group_ID,
                bp.C_BP_AccountType_ID, bp.C_BP_SalesGroup_ID, bp.C_BP_Segment_ID, bp.C_BP_IndustryType_ID,
                i.SalesRep_ID, i.C_SalesRegion_ID, i.AD_OrgTrx_ID, i.C_Campaign_ID, i.C_Project_ID, i.C_Activity_ID, i.M_PriceList_ID,
                i.DateInvoiced, i.DateAcct, i.C_Currency_ID, i.C_ConversionType_ID, i.PaymentRule, i.C_PaymentTerm_ID,
                i.IsPayScheduleValid, i.InvoiceCollectionType, i.IsInDispute,
                CASE WHEN SUBSTR(d.DocBaseType, 3, 1) = 'C' THEN -1 ELSE 1 END,
                CASE WHEN SUBSTR(d.DocBaseType, 2, 1) = 'P' THEN -1 ELSE 1 END,
                CAST(NULL AS NUMERIC),
                COALESCE(al.Paid, 0),
                1 / POWER(10, cur.StdPrecision), cur.StdPrecision,
                ips.C_InvoicePaySchedule_ID, ips.DueAmt,
                COALESCE(cum.DueBefore, 0),
                CAST(ips.DueDate AS DATE) - CAST(i.DateInvoiced AS DATE),
                ips.DueDate,
                CAST(CURRENT_DATE AS DATE) - CAST(ips.DueDate AS DATE),
                ips.DiscountDate,
                ips.DiscountAmt,
                ips.DueAmt,
                ROUND(COALESCE(al.Paid, 0), 2)
            FROM C_Invoice i
                INNER JOIN C_DocType d ON (d.C_DocType_ID = i.C_DocType_ID)
                INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = i.C_BPartner_ID)
                INNER JOIN C_BPartner_Location bpl ON (bpl.C_BPartner_Location_ID = i.C_BPartner_Location_ID)
                INNER JOIN C_Location loc ON (loc.C_Location_ID = bpl.C_Location_ID)
                INNER JOIN C_Currency cur ON (cur.C_Currency_ID = i.C_Currency_ID)
                INNER JOIN C_InvoicePaySchedule ips ON (ips.C_Invoice_ID = i.C_Invoice_ID AND ips.IsValid = 'Y')
                LEFT JOIN LATERAL (
                    SELECT SUM(prev.DueAmt) AS DueBefore
                    FROM C_InvoicePaySchedule prev
                    WHERE prev.C_Invoice_ID = ips.C_Invoice_ID AND prev.IsValid = 'Y'
                        AND (prev.DueDate < ips.DueDate OR (prev.DueDate = ips.DueDate AND prev.C_InvoicePaySchedule_ID < ips.C_InvoicePaySchedule_ID))
                ) cum ON TRUE
                LEFT JOIN LATERAL (
                    SELECT SUM(CASE WHEN h.C_Currency_ID = i.C_Currency_ID THEN line.Amount + line.DiscountAmt + line.WriteOffAmt
                            ELSE currencyConvert(line.Amount + line.DiscountAmt + line.WriteOffAmt, h.C_Currency_ID, i.C_Currency_ID, h.DateTrx, NULL, h.AD_Client_ID, h.AD_Org_ID) END) AS Paid
                    FROM C_AllocationLine line
                        INNER JOIN C_AllocationHdr h ON (h.C_AllocationHdr_ID = line.C_AllocationHdr_ID)
                    WHERE line.C_Invoice_ID = i.C_Invoice_ID AND h.DocStatus IN ('CO', 'CL')
                ) al ON TRUE
            WHERE i.DocStatus IN ('CO', 'CL') AND i.IsPayScheduleValid = 'Y'
        ) b
    ) y
) x
WHERE x.OpenAmt <> 0;
