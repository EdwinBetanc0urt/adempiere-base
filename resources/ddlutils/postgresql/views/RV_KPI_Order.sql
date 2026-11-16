CREATE OR REPLACE VIEW RV_KPI_Order
(RV_KPI_Order_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 C_Order_ID, DocumentNo, POReference, Description,
 C_BPartner_ID, C_BP_Group_ID, C_BPartner_Location_ID, AD_User_ID, SalesRep_ID,
 M_Warehouse_ID, C_Project_ID, C_Campaign_ID, C_Activity_ID, AD_OrgTrx_ID,
 C_SalesRegion_ID, C_OrderSource_ID, C_PaymentTerm_ID, M_PriceList_ID, M_Shipper_ID,
 PaymentRule, DeliveryRule, DeliveryViaRule, PriorityRule,
 C_DocType_ID, DocBaseType, DocSubTypeSO, DocStatus, IsSOTrx, IsAllowToInvoice,
 C_Currency_ID, C_ConversionType_ID,
 DateOrdered, DatePromised, DateAcct, LastShipDate,
 Sign, GrandTotal, TotalLines, AmtToInvoice,
 QtyOrdered, QtyDelivered, QtyInvoiced, QtyToDeliver, QtyToInvoice,
 OrderCount, IsReturn, IsDelivered, IsInvoiced, HasBackorder, IsToInvoice,
 IsReceivedNotInvoiced, IsInvoicedNotReceived, IsLate)
AS
SELECT x.C_Order_ID,
    x.AD_Client_ID, x.AD_Org_ID, x.IsActive, x.Created, x.Updated,
    x.C_Order_ID, x.DocumentNo, x.POReference, x.Description,
    x.C_BPartner_ID, x.C_BP_Group_ID, x.C_BPartner_Location_ID, x.AD_User_ID, x.SalesRep_ID,
    x.M_Warehouse_ID, x.C_Project_ID, x.C_Campaign_ID, x.C_Activity_ID, x.AD_OrgTrx_ID,
    x.C_SalesRegion_ID, x.C_OrderSource_ID, x.C_PaymentTerm_ID, x.M_PriceList_ID, x.M_Shipper_ID,
    x.PaymentRule, x.DeliveryRule, x.DeliveryViaRule, x.PriorityRule,
    x.C_DocType_ID, x.DocBaseType, x.DocSubTypeSO, x.DocStatus, x.IsSOTrx, x.IsAllowToInvoice,
    x.C_Currency_ID, x.C_ConversionType_ID,
    x.DateOrdered, x.DatePromised, x.DateAcct, x.LastShipDate,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.GrandTotal,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.TotalLines,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.AmtToInvoice,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.QtyOrdered,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.QtyDelivered,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.QtyInvoiced,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.QtyToDeliver,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * x.QtyToInvoice,
    1,
    CASE WHEN x.DocSubTypeSO = 'RM' THEN 1 ELSE 0 END,
    x.IsDelivered,
    x.IsInvoiced,
    CASE
        WHEN x.DocStatus = 'CO' AND x.HdrIsDelivered = 'N' THEN 1
        ELSE 0
    END,
    CASE
        WHEN x.DocStatus = 'CO' AND x.HdrIsInvoiced = 'N' THEN 1
        ELSE 0
    END,
    CASE
        WHEN x.DocStatus IN ('CO', 'CL') AND x.QtyInvoiced = 0 AND x.IsDelivered = 1 THEN 1
        ELSE 0
    END,
    CASE
        WHEN x.DocStatus IN ('CO', 'CL') AND x.QtyInvoiced > 0 AND x.QtyDelivered = 0 THEN 1
        ELSE 0
    END,
    CASE
        WHEN x.IsDelivered = 1 AND x.DatePromised IS NOT NULL AND x.LastShipDate IS NOT NULL
         AND CAST(x.LastShipDate AS date) > CAST(x.DatePromised AS date) THEN 1
        WHEN x.IsDelivered = 0 AND x.DocStatus = 'CO' AND x.DatePromised IS NOT NULL
         AND CAST(x.DatePromised AS date) < CURRENT_DATE THEN 1
        ELSE 0
    END
FROM (
    SELECT b.*,
        CASE
            WHEN b.DocStatus IN ('CO', 'CL')
             AND (b.HdrIsDelivered = 'Y' OR (b.QtyOrdered > 0 AND b.QtyDelivered >= b.QtyOrdered)) THEN 1
            ELSE 0
        END AS IsDelivered,
        CASE
            WHEN b.DocStatus IN ('CO', 'CL')
             AND (b.HdrIsInvoiced = 'Y' OR (b.QtyOrdered > 0 AND b.QtyInvoiced >= b.QtyOrdered)) THEN 1
            ELSE 0
        END AS IsInvoiced
    FROM (
        SELECT o.AD_Client_ID, o.AD_Org_ID, o.IsActive, o.Created, o.Updated,
            o.C_Order_ID, o.DocumentNo, o.POReference, o.Description,
            o.C_BPartner_ID, bp.C_BP_Group_ID, o.C_BPartner_Location_ID, o.AD_User_ID, o.SalesRep_ID,
            o.M_Warehouse_ID, o.C_Project_ID, o.C_Campaign_ID, o.C_Activity_ID, o.AD_OrgTrx_ID,
            o.C_SalesRegion_ID, o.C_OrderSource_ID, o.C_PaymentTerm_ID, o.M_PriceList_ID, o.M_Shipper_ID,
            o.PaymentRule, o.DeliveryRule, o.DeliveryViaRule, o.PriorityRule,
            o.C_DocType_ID, dt.DocBaseType, dt.DocSubTypeSO, o.DocStatus, o.IsSOTrx,
            COALESCE(o.IsAllowToInvoice, 'N') AS IsAllowToInvoice,
            o.C_Currency_ID, o.C_ConversionType_ID,
            o.DateOrdered, o.DatePromised, o.DateAcct, s.LastShipDate,
            COALESCE(o.IsDelivered, 'N') AS HdrIsDelivered,
            COALESCE(o.IsInvoiced, 'N') AS HdrIsInvoiced,
            o.GrandTotal, o.TotalLines,
            COALESCE(l.AmtToInvoice, 0) AS AmtToInvoice,
            COALESCE(l.QtyOrdered, 0) AS QtyOrdered,
            COALESCE(l.QtyDelivered, 0) AS QtyDelivered,
            COALESCE(l.QtyInvoiced, 0) AS QtyInvoiced,
            COALESCE(l.QtyOrdered, 0) - COALESCE(l.QtyDelivered, 0) AS QtyToDeliver,
            COALESCE(l.QtyOrdered, 0) - COALESCE(l.QtyInvoiced, 0) AS QtyToInvoice
        FROM C_Order o
            INNER JOIN C_DocType dt ON (dt.C_DocType_ID = o.C_DocType_ID)
            INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = o.C_BPartner_ID)
            LEFT JOIN (
                SELECT ol.C_Order_ID,
                    SUM(ol.QtyOrdered) AS QtyOrdered,
                    SUM(ol.QtyDelivered) AS QtyDelivered,
                    SUM(ol.QtyInvoiced) AS QtyInvoiced,
                    SUM((ol.QtyOrdered - ol.QtyInvoiced) * ol.PriceActual) AS AmtToInvoice
                FROM C_OrderLine ol
                WHERE ol.IsActive = 'Y' AND ol.IsDescription = 'N'
                GROUP BY ol.C_Order_ID
            ) l ON (l.C_Order_ID = o.C_Order_ID)
            LEFT JOIN (
                SELECT ol.C_Order_ID, MAX(io.MovementDate) AS LastShipDate
                FROM M_InOutLine iol
                    INNER JOIN M_InOut io ON (io.M_InOut_ID = iol.M_InOut_ID)
                    INNER JOIN C_OrderLine ol ON (ol.C_OrderLine_ID = iol.C_OrderLine_ID)
                WHERE io.DocStatus IN ('CO', 'CL') AND iol.IsActive = 'Y' AND iol.MovementQty <> 0
                GROUP BY ol.C_Order_ID
            ) s ON (s.C_Order_ID = o.C_Order_ID)
    ) b
) x;
