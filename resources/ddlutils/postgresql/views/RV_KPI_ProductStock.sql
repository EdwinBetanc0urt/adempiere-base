CREATE OR REPLACE VIEW RV_KPI_ProductStock
(RV_KPI_ProductStock_ID, AD_Client_ID, AD_Org_ID, IsActive,
 M_Product_ID, ProductValue, ProductName, SKU, UPC,
 M_Product_Category_ID, M_Product_Group_ID, M_Product_Class_ID, M_Product_Classification_ID, C_UOM_ID,
 M_Warehouse_ID, DateSnapshot,
 QtyOnHand, QtyReserved, QtyOnOrder, DemandOV, ProductCount, IsNoStockWithDemand)
AS
SELECT CAST(ROW_NUMBER() OVER (ORDER BY p.M_Product_ID, w.M_Warehouse_ID) AS NUMERIC(10,0)),
    w.AD_Client_ID, w.AD_Org_ID, CAST('Y' AS CHAR(1)),
    p.M_Product_ID, p.Value, p.Name, p.SKU, p.UPC,
    p.M_Product_Category_ID, p.M_Product_Group_ID, p.M_Product_Class_ID, p.M_Product_Classification_ID, p.C_UOM_ID,
    w.M_Warehouse_ID, CURRENT_DATE,
    COALESCE(s.QtyOnHand, 0),
    COALESCE(s.QtyReserved, 0),
    COALESCE(s.QtyOrdered, 0),
    COALESCE(d.DemandOV, 0),
    1,
    CASE
        WHEN COALESCE(s.QtyOnHand, 0) <= 0 AND COALESCE(d.DemandOV, 0) > 0 THEN 1
        ELSE 0
    END
FROM (
        SELECT st.M_Product_ID, l.M_Warehouse_ID,
            SUM(st.QtyOnHand) AS QtyOnHand,
            SUM(st.QtyReserved) AS QtyReserved,
            SUM(st.QtyOrdered) AS QtyOrdered
        FROM M_Storage st
            INNER JOIN M_Locator l ON (l.M_Locator_ID = st.M_Locator_ID)
        WHERE st.IsActive = 'Y'
        GROUP BY st.M_Product_ID, l.M_Warehouse_ID
    ) s
    FULL OUTER JOIN (
        SELECT ol.M_Product_ID, COALESCE(ol.M_Warehouse_ID, o.M_Warehouse_ID) AS M_Warehouse_ID,
            SUM(ol.QtyOrdered - ol.QtyDelivered) AS DemandOV
        FROM C_OrderLine ol
            INNER JOIN C_Order o ON (o.C_Order_ID = ol.C_Order_ID)
            INNER JOIN C_DocType dt ON (dt.C_DocType_ID = o.C_DocType_ID)
        WHERE o.IsSOTrx = 'Y' AND o.DocStatus = 'CO' AND dt.DocSubTypeSO <> 'RM' AND ol.IsActive = 'Y'
            AND ol.M_Product_ID IS NOT NULL AND ol.QtyOrdered > ol.QtyDelivered
        GROUP BY ol.M_Product_ID, COALESCE(ol.M_Warehouse_ID, o.M_Warehouse_ID)
    ) d ON (d.M_Product_ID = s.M_Product_ID AND d.M_Warehouse_ID = s.M_Warehouse_ID)
    INNER JOIN M_Product p ON (p.M_Product_ID = COALESCE(s.M_Product_ID, d.M_Product_ID))
    INNER JOIN M_Warehouse w ON (w.M_Warehouse_ID = COALESCE(s.M_Warehouse_ID, d.M_Warehouse_ID))
WHERE p.IsActive = 'Y' AND p.IsStocked = 'Y' AND p.ProductType = 'I' AND p.IsSummary = 'N';
