CREATE OR REPLACE VIEW RV_KPI_ProductSales
(RV_KPI_ProductSales_ID, AD_Client_ID, AD_Org_ID, IsActive,
 M_Product_ID, ProductValue, ProductName, SKU, UPC,
 M_Product_Category_ID, M_Product_Group_ID, M_Product_Class_ID, M_Product_Classification_ID, C_UOM_ID,
 DateSnapshot, QtyOnHand, LastSaleDate, LastReceiptDate, ProductCount)
AS
SELECT p.M_Product_ID,
    p.AD_Client_ID, p.AD_Org_ID, p.IsActive,
    p.M_Product_ID, p.Value, p.Name, p.SKU, p.UPC,
    p.M_Product_Category_ID, p.M_Product_Group_ID, p.M_Product_Class_ID, p.M_Product_Classification_ID, p.C_UOM_ID,
    CURRENT_DATE,
    COALESCE(s.QtyOnHand, 0),
    COALESCE(CAST(t.LastSaleDate AS date), DATE '1900-01-01'),
    CAST(t.LastReceiptDate AS date),
    1
FROM M_Product p
    LEFT JOIN (
        SELECT st.M_Product_ID, SUM(st.QtyOnHand) AS QtyOnHand
        FROM M_Storage st
        WHERE st.IsActive = 'Y'
        GROUP BY st.M_Product_ID
    ) s ON (s.M_Product_ID = p.M_Product_ID)
    LEFT JOIN (
        SELECT tr.M_Product_ID,
            MAX(tr.MovementDate) FILTER (WHERE tr.MovementType = 'C-') AS LastSaleDate,
            MAX(tr.MovementDate) FILTER (WHERE tr.MovementType = 'V+') AS LastReceiptDate
        FROM M_Transaction tr
        WHERE tr.MovementType IN ('C-', 'V+')
        GROUP BY tr.M_Product_ID
    ) t ON (t.M_Product_ID = p.M_Product_ID)
WHERE p.IsActive = 'Y' AND p.IsStocked = 'Y' AND p.ProductType = 'I' AND p.IsSummary = 'N';
