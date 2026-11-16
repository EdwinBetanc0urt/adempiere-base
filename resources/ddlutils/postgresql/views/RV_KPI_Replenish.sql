CREATE OR REPLACE VIEW RV_KPI_Replenish
(RV_KPI_Replenish_ID, AD_Client_ID, AD_Org_ID, IsActive,
 M_Product_ID, M_Product_Category_ID, M_Product_Group_ID, M_Product_Class_ID, M_Product_Classification_ID, C_UOM_ID,
 M_Warehouse_ID, M_WarehouseSource_ID, M_Locator_ID, C_BPartner_ID, ReplenishType,
 Level_Min, Level_Max, QtyAvailable, QtyOnHand, QtyReserved, QtyOrdered, QtyToOrder, Order_Min, Order_Pack)
AS
SELECT CAST(ROW_NUMBER() OVER (ORDER BY r.M_Warehouse_ID, r.M_Product_ID, r.M_Locator_ID) AS NUMERIC(10,0)),
    r.AD_Client_ID, r.AD_Org_ID, CAST('Y' AS CHAR(1)),
    r.M_Product_ID, r.M_Product_Category_ID, r.M_Product_Group_ID, r.M_Product_Class_ID, r.M_Product_Classification_ID, r.C_UOM_ID,
    r.M_Warehouse_ID, r.M_WarehouseSource_ID, r.M_Locator_ID, r.C_BPartner_ID, r.ReplenishType,
    r.Level_Min, r.Level_Max, r.QtyAvailable, r.QtyOnHand, r.QtyReserved, r.QtyOrdered,
    CASE
        WHEN r.Order_Pack > 0 AND r.QtyToOrder > 0
            AND MOD(CASE WHEN r.QtyToOrder < r.Order_Min THEN r.Order_Min ELSE r.QtyToOrder END, r.Order_Pack) <> 0
        THEN CASE WHEN r.QtyToOrder < r.Order_Min THEN r.Order_Min ELSE r.QtyToOrder END
            - MOD(CASE WHEN r.QtyToOrder < r.Order_Min THEN r.Order_Min ELSE r.QtyToOrder END, r.Order_Pack) + r.Order_Pack
        ELSE r.QtyToOrder
    END,
    r.Order_Min, r.Order_Pack
FROM (
    SELECT m.AD_Client_ID, m.AD_Org_ID, m.M_Product_ID, m.ReplenishType,
        COALESCE(m.Level_Min, 0) AS Level_Min,
        COALESCE(m.Level_Max, 0) AS Level_Max,
        COALESCE(s.QtyAvailable, 0) AS QtyAvailable,
        COALESCE(s.QtyOnHand, 0) AS QtyOnHand,
        COALESCE(s.QtyReserved, 0) AS QtyReserved,
        COALESCE(s.QtyOrdered, 0) AS QtyOrdered,
        COALESCE(CASE
            WHEN m.ReplenishType = '1' THEN
                CASE WHEN (COALESCE(s.QtyOnHand, 0) - COALESCE(s.QtyReserved, 0) + COALESCE(s.QtyOrdered, 0)) <= m.Level_Min
                    THEN m.Level_Max - (COALESCE(s.QtyOnHand, 0) - COALESCE(s.QtyReserved, 0) + COALESCE(s.QtyOrdered, 0))
                    ELSE 0 END
            WHEN m.ReplenishType = '2' THEN m.Level_Max - (COALESCE(s.QtyOnHand, 0) - COALESCE(s.QtyReserved, 0) + COALESCE(s.QtyOrdered, 0))
            ELSE 0
        END, 0) AS QtyToOrder,
        p.M_Product_Category_ID, p.M_Product_Group_ID, p.M_Product_Class_ID, p.M_Product_Classification_ID, p.C_UOM_ID,
        m.M_Locator_ID, m.M_Warehouse_ID, m.M_WarehouseSource_ID,
        po.C_BPartner_ID,
        COALESCE(po.Order_Min, 0) AS Order_Min,
        COALESCE(po.Order_Pack, 0) AS Order_Pack
    FROM M_Replenish m
        INNER JOIN M_Product p ON (p.M_Product_ID = m.M_Product_ID)
        LEFT JOIN LATERAL (
            SELECT ppo.C_BPartner_ID, ppo.Order_Min, ppo.Order_Pack
            FROM M_Product_PO ppo
            WHERE ppo.M_Product_ID = m.M_Product_ID AND ppo.IsActive = 'Y' AND ppo.IsCurrentVendor = 'Y'
                AND ppo.AD_Org_ID IN (0, m.AD_Org_ID)
            ORDER BY ppo.AD_Org_ID DESC
            LIMIT 1
        ) po ON TRUE
        LEFT JOIN (
            SELECT st.M_Product_ID, lc.M_Warehouse_ID,
                SUM(st.QtyOnHand) AS QtyOnHand, SUM(st.QtyOrdered) AS QtyOrdered, SUM(st.QtyReserved) AS QtyReserved,
                SUM(st.QtyOnHand - st.QtyReserved) AS QtyAvailable
            FROM M_Storage st
                INNER JOIN M_Locator lc ON (lc.M_Locator_ID = st.M_Locator_ID)
                INNER JOIN M_Product sp ON (sp.M_Product_ID = st.M_Product_ID)
            GROUP BY st.M_Product_ID, lc.M_Warehouse_ID
        ) s ON (s.M_Product_ID = m.M_Product_ID AND s.M_Warehouse_ID = m.M_Warehouse_ID)
    WHERE m.IsActive = 'Y' AND p.IsActive = 'Y'
) r
WHERE r.QtyToOrder > 0 OR r.ReplenishType IN ('0', '9');
