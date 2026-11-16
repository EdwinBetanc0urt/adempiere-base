CREATE OR REPLACE VIEW RV_KPI_Requisition
(RV_KPI_Requisition_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 M_Requisition_ID, M_RequisitionLine_ID, DocumentNo, Description, LineDescription, DocStatus, IsApproved, Processed,
 C_DocType_ID, AD_User_ID, M_PriceList_ID, M_Warehouse_ID, PriorityRule,
 AD_OrgTrx_ID, C_Campaign_ID, C_Project_ID, C_Activity_ID,
 DateDoc, DateRequired, Line, M_Product_ID, M_Product_Category_ID, M_Product_Group_ID, C_UOM_ID, C_BPartner_ID,
 C_Order_ID, C_OrderLine_ID, C_Charge_ID,
 Qty, QtyOrdered, PriceActual, LineNetAmt, TotalLines, GrandTotal)
AS
SELECT l.M_RequisitionLine_ID, r.AD_Client_ID, r.AD_Org_ID, r.IsActive, r.Created, r.Updated,
    r.M_Requisition_ID, l.M_RequisitionLine_ID, r.DocumentNo, r.Description, l.Description, r.DocStatus, r.IsApproved, r.Processed,
    r.C_DocType_ID, r.AD_User_ID, r.M_PriceList_ID, r.M_Warehouse_ID, r.PriorityRule,
    r.AD_OrgTrx_ID, r.C_Campaign_ID, r.C_Project_ID, r.C_Activity_ID,
    r.DateDoc, r.DateRequired, l.Line, l.M_Product_ID, p.M_Product_Category_ID, p.M_Product_Group_ID, l.C_UOM_ID, l.C_BPartner_ID,
    l.C_Order_ID, l.C_OrderLine_ID, l.C_Charge_ID,
    l.Qty,
    CASE WHEN l.C_OrderLine_ID IS NOT NULL THEN l.Qty ELSE 0 END,
    l.PriceActual, l.LineNetAmt, r.TotalLines, r.GrandTotal
FROM M_Requisition r
    INNER JOIN M_RequisitionLine l ON (l.M_Requisition_ID = r.M_Requisition_ID)
    LEFT JOIN M_Product p ON (p.M_Product_ID = l.M_Product_ID);
