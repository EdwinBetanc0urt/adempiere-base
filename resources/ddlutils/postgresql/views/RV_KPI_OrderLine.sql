CREATE OR REPLACE VIEW RV_KPI_OrderLine
(RV_KPI_OrderLine_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 C_OrderLine_ID, C_Order_ID, DocumentNo, Line, Description, POReference,
 C_BPartner_ID, C_BP_Group_ID, C_BPartner_Location_ID, SalesRep_ID,
 C_DocType_ID, DocBaseType, DocSubTypeSO, DocStatus, IsSOTrx,
 C_Currency_ID, C_ConversionType_ID,
 DateOrdered, DatePromised, DateAcct,
 M_Product_ID, M_Product_Category_ID, M_Product_Group_ID, M_Product_Class_ID, M_Product_Classification_ID,
 C_Charge_ID, C_UOM_ID, M_Warehouse_ID, C_Project_ID, C_Campaign_ID, C_Activity_ID, AD_OrgTrx_ID,
 C_SalesRegion_ID, Sign, IsReturn, PriceActual,
 QtyOrdered, QtyDelivered, QtyInvoiced, QtyToDeliver, QtyToInvoice, QtyLostSales,
 LineNetAmt, AmtToInvoice, AmtLostSales, LineCount)
AS
SELECT ol.C_OrderLine_ID,
    ol.AD_Client_ID, ol.AD_Org_ID, ol.IsActive, ol.Created, ol.Updated,
    ol.C_OrderLine_ID, o.C_Order_ID, o.DocumentNo, ol.Line, ol.Description, o.POReference,
    ol.C_BPartner_ID, bp.C_BP_Group_ID, ol.C_BPartner_Location_ID, o.SalesRep_ID,
    o.C_DocType_ID, dt.DocBaseType, dt.DocSubTypeSO, o.DocStatus, o.IsSOTrx,
    o.C_Currency_ID, o.C_ConversionType_ID,
    ol.DateOrdered, ol.DatePromised, o.DateAcct,
    ol.M_Product_ID, ol.M_Product_Category_ID, ol.M_Product_Group_ID, ol.M_Product_Class_ID, ol.M_Product_Classification_ID,
    ol.C_Charge_ID, ol.C_UOM_ID, ol.M_Warehouse_ID, ol.C_Project_ID, ol.C_Campaign_ID, ol.C_Activity_ID, ol.AD_OrgTrx_ID,
    o.C_SalesRegion_ID,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN 1 ELSE 0 END,
    ol.PriceActual,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.QtyOrdered,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.QtyDelivered,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.QtyInvoiced,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * (ol.QtyOrdered - ol.QtyDelivered),
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * (ol.QtyOrdered - ol.QtyInvoiced),
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.QtyLostSales,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.LineNetAmt,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * (ol.QtyOrdered - ol.QtyInvoiced) * ol.PriceActual,
    CASE WHEN dt.DocSubTypeSO = 'RM' THEN -1 ELSE 1 END * ol.QtyLostSales * ol.PriceActual,
    1
FROM C_OrderLine ol
    INNER JOIN C_Order o ON (o.C_Order_ID = ol.C_Order_ID)
    INNER JOIN C_DocType dt ON (dt.C_DocType_ID = o.C_DocType_ID)
    INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = ol.C_BPartner_ID)
WHERE ol.IsDescription = 'N';
