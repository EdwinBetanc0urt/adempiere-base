CREATE OR REPLACE VIEW RV_KPI_InventoryValue
(RV_KPI_InventoryValue_ID, AD_Client_ID, AD_Org_ID, IsActive,
 M_Product_ID, ProductValue, ProductName, SKU, UPC,
 M_Product_Category_ID, M_Product_Group_ID, M_Product_Class_ID, M_Product_Classification_ID, C_UOM_ID,
 M_Warehouse_ID, C_AcctSchema_ID, DateSnapshot, C_Currency_ID,
 QtyOnHand, CostCurrent, CostMaterial, ValueAmt, ValueMaterialAmt)
AS
SELECT CAST(ROW_NUMBER() OVER (ORDER BY s.M_Product_ID, s.M_Warehouse_ID) AS NUMERIC(10,0)),
    w.AD_Client_ID, w.AD_Org_ID, CAST('Y' AS CHAR(1)),
    s.M_Product_ID, p.Value, p.Name, p.SKU, p.UPC,
    p.M_Product_Category_ID, p.M_Product_Group_ID, p.M_Product_Class_ID, p.M_Product_Classification_ID, p.C_UOM_ID,
    s.M_Warehouse_ID, acs.C_AcctSchema_ID, CURRENT_DATE, acs.C_Currency_ID,
    s.QtyOnHand,
    COALESCE(cost.CostCurrent, 0),
    COALESCE(cost.CostMaterial, 0),
    s.QtyOnHand * COALESCE(cost.CostCurrent, 0),
    s.QtyOnHand * COALESCE(cost.CostMaterial, 0)
FROM (
        SELECT st.M_Product_ID, l.M_Warehouse_ID, SUM(st.QtyOnHand) AS QtyOnHand
        FROM M_Storage st
            INNER JOIN M_Locator l ON (l.M_Locator_ID = st.M_Locator_ID)
        WHERE st.IsActive = 'Y'
        GROUP BY st.M_Product_ID, l.M_Warehouse_ID
        HAVING SUM(st.QtyOnHand) <> 0
    ) s
    INNER JOIN M_Product p ON (p.M_Product_ID = s.M_Product_ID)
    INNER JOIN M_Warehouse w ON (w.M_Warehouse_ID = s.M_Warehouse_ID)
    INNER JOIN AD_ClientInfo ci ON (ci.AD_Client_ID = w.AD_Client_ID)
    INNER JOIN C_AcctSchema acs ON (acs.C_AcctSchema_ID = ci.C_AcctSchema1_ID)
    LEFT JOIN (
        SELECT c.M_Product_ID, c.AD_Org_ID, c.C_AcctSchema_ID,
            SUM(c.CurrentCostPrice) AS CostCurrent,
            SUM(CASE WHEN ce.CostElementType = 'M' THEN c.CurrentCostPrice ELSE 0 END) AS CostMaterial
        FROM M_Cost c
            INNER JOIN C_AcctSchema cs ON (cs.C_AcctSchema_ID = c.C_AcctSchema_ID)
            INNER JOIN M_CostElement ce ON (ce.M_CostElement_ID = c.M_CostElement_ID)
        WHERE c.IsActive = 'Y' AND c.M_AttributeSetInstance_ID = 0
            AND c.M_CostType_ID = cs.M_CostType_ID
            AND (ce.CostingMethod IS NULL OR ce.CostingMethod = cs.CostingMethod)
        GROUP BY c.M_Product_ID, c.AD_Org_ID, c.C_AcctSchema_ID
    ) cost ON (cost.M_Product_ID = s.M_Product_ID
        AND cost.C_AcctSchema_ID = acs.C_AcctSchema_ID
        AND cost.AD_Org_ID = CASE WHEN acs.CostingLevel = 'O' THEN w.AD_Org_ID ELSE 0 END)
WHERE p.IsActive = 'Y' AND p.IsStocked = 'Y' AND p.ProductType = 'I';
