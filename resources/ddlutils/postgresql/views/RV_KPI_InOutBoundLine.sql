CREATE OR REPLACE VIEW RV_KPI_InOutBoundLine
(RV_KPI_InOutBoundLine_ID, AD_Client_ID, AD_Org_ID, IsActive,
 WM_InOutBoundLine_ID, WM_InOutBound_ID, DocumentNo, POReference, TrackingNo,
 C_BPartner_ID, C_Order_ID, C_OrderLine_ID, C_DocType_ID, DocStatus, IsSOTrx,
 SalesRep_ID, M_Warehouse_ID, M_Shipper_ID, C_Project_ID, C_Campaign_ID, C_Activity_ID, AD_OrgTrx_ID,
 DeliveryRule, DeliveryViaRule, PriorityRule, IsInTransit,
 M_Product_ID, M_Product_Category_ID, M_Product_Group_ID, C_UOM_ID,
 MovementQty, PickedQty, DateTrx, PickDate, ShipDate, LineCount)
AS
SELECT l.WM_InOutBoundLine_ID,
    l.AD_Client_ID, l.AD_Org_ID, l.IsActive,
    l.WM_InOutBoundLine_ID, l.WM_InOutBound_ID, h.DocumentNo, h.POReference, h.TrackingNo,
    COALESCE(o.C_BPartner_ID, ddo.C_BPartner_ID, bp.C_BPartner_ID),
    l.C_Order_ID, l.C_OrderLine_ID, h.C_DocType_ID, h.DocStatus, h.IsSOTrx,
    h.SalesRep_ID, h.M_Warehouse_ID, h.M_Shipper_ID, l.C_Project_ID, l.C_Campaign_ID, l.C_Activity_ID, l.AD_OrgTrx_ID,
    h.DeliveryRule, h.DeliveryViaRule, h.PriorityRule, h.IsInTransit,
    l.M_Product_ID, p.M_Product_Category_ID, p.M_Product_Group_ID, l.C_UOM_ID,
    l.MovementQty, l.PickedQty, h.DateTrx, l.PickDate, l.ShipDate,
    1
FROM WM_InOutBoundLine l
    INNER JOIN WM_InOutBound h ON (h.WM_InOutBound_ID = l.WM_InOutBound_ID)
    LEFT JOIN C_OrderLine ol ON (ol.C_OrderLine_ID = l.C_OrderLine_ID)
    LEFT JOIN C_Order o ON (o.C_Order_ID = ol.C_Order_ID)
    LEFT JOIN DD_OrderLine dol ON (dol.DD_OrderLine_ID = l.DD_OrderLine_ID)
    LEFT JOIN DD_Order ddo ON (ddo.DD_Order_ID = dol.DD_Order_ID)
    LEFT JOIN PP_Order_BOMLine pol ON (pol.PP_Order_BOMLine_ID = l.PP_Order_BOMLine_ID)
    LEFT JOIN PP_Order po ON (po.PP_Order_ID = pol.PP_Order_ID)
    LEFT JOIN C_BPartner bp ON (bp.AD_OrgBP_ID = po.AD_Org_ID)
    LEFT JOIN M_Product p ON (p.M_Product_ID = l.M_Product_ID)
WHERE l.IsDescription = 'N';
