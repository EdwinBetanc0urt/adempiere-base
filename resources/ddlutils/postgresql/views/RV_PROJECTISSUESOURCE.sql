CREATE OR REPLACE VIEW RV_PROJECTISSUESOURCE
(RV_PROJECTISSUESOURCE_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY,
 UPDATED, UPDATEDBY, CREATEFROMTYPE, C_PROJECT_ID, C_PROJECTPHASE_ID, C_PROJECTTASK_ID,
 C_PROJECTLINE_ID, C_ORDER_ID, C_ORDERLINE_ID, DATEDOC, C_BPARTNER_ID, M_WAREHOUSE_ID,
 M_LOCATOR_ID, M_PRODUCT_ID, QTYAVAILABLE, MOVEMENTQTY)
AS
SELECT CAST(ROW_NUMBER() OVER (ORDER BY s.CreateFromType, s.C_ProjectLine_ID, s.C_OrderLine_ID, s.M_Locator_ID) AS NUMERIC(10,0)),
    s.AD_Client_ID, s.AD_Org_ID, s.IsActive, s.Created, s.CreatedBy, s.Updated, s.UpdatedBy,
    s.CreateFromType, s.C_Project_ID, s.C_ProjectPhase_ID, s.C_ProjectTask_ID, s.C_ProjectLine_ID,
    s.C_Order_ID, s.C_OrderLine_ID, s.DateDoc, s.C_BPartner_ID, s.M_Warehouse_ID, s.M_Locator_ID,
    s.M_Product_ID, s.QtyAvailable, s.MovementQty
FROM (
    -- Purchase order lines of a project line pending to be received
    SELECT ol.AD_Client_ID, ol.AD_Org_ID, ol.IsActive, ol.Created, ol.CreatedBy, ol.Updated, ol.UpdatedBy,
        CAST('O' AS CHAR(1)) AS CreateFromType,
        pl.C_Project_ID, pl.C_ProjectPhase_ID, pl.C_ProjectTask_ID, pl.C_ProjectLine_ID,
        o.C_Order_ID, ol.C_OrderLine_ID, o.DateOrdered AS DateDoc, o.C_BPartner_ID, o.M_Warehouse_ID,
        CAST(NULL AS NUMERIC(10,0)) AS M_Locator_ID,
        ol.M_Product_ID,
        ol.QtyOrdered - ol.QtyDelivered AS QtyAvailable,
        ol.QtyOrdered - ol.QtyDelivered AS MovementQty
    FROM C_OrderLine ol
        INNER JOIN C_Order o ON (o.C_Order_ID = ol.C_Order_ID)
        INNER JOIN C_ProjectLine pl ON (pl.C_ProjectLine_ID = ol.C_ProjectLine_ID)
    WHERE o.IsSOTrx = 'N'
        AND o.DocStatus = 'CO'
        AND ol.M_Product_ID IS NOT NULL
        AND ol.QtyOrdered - ol.QtyDelivered > 0
    UNION ALL
    -- Stock by locator of the products of the open project lines
    SELECT pl.AD_Client_ID, l.AD_Org_ID, pl.IsActive, pl.Created, pl.CreatedBy, pl.Updated, pl.UpdatedBy,
        CAST('I' AS CHAR(1)) AS CreateFromType,
        pl.C_Project_ID, pl.C_ProjectPhase_ID, pl.C_ProjectTask_ID, pl.C_ProjectLine_ID,
        CAST(NULL AS NUMERIC(10,0)) AS C_Order_ID, CAST(NULL AS NUMERIC(10,0)) AS C_OrderLine_ID,
        CAST(NULL AS TIMESTAMP) AS DateDoc, CAST(NULL AS NUMERIC(10,0)) AS C_BPartner_ID,
        l.M_Warehouse_ID, st.M_Locator_ID,
        st.M_Product_ID,
        st.QtyAvailable,
        LEAST(st.QtyAvailable, GREATEST(COALESCE(pl.PlannedQty, 0) - COALESCE(pl.QtyDelivered, 0), 0)) AS MovementQty
    FROM (SELECT s.AD_Client_ID, s.M_Locator_ID, s.M_Product_ID, SUM(s.QtyOnHand - s.QtyReserved) AS QtyAvailable
          FROM M_Storage s
          GROUP BY s.AD_Client_ID, s.M_Locator_ID, s.M_Product_ID) st
        INNER JOIN M_Locator l ON (l.M_Locator_ID = st.M_Locator_ID)
        INNER JOIN C_ProjectLine pl ON (pl.M_Product_ID = st.M_Product_ID AND pl.AD_Client_ID = st.AD_Client_ID)
        INNER JOIN C_Project p ON (p.C_Project_ID = pl.C_Project_ID)
    WHERE st.QtyAvailable > 0
        AND pl.IsSummary = 'N'
        AND p.IsActive = 'Y'
        AND p.Processed = 'N'
) s;
