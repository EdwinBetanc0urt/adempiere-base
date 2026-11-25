DROP VIEW IF EXISTS RV_InOutRMA_CreateFrom;
CREATE OR REPLACE VIEW RV_InOutRMA_CreateFrom AS
--  From Order
SELECT l.AD_Client_ID, l.AD_Org_ID, l.CreatedBy, l.Created, l.UpdatedBy, l.Updated, l.IsActive, l.C_OrderLine_ID AS RV_InOutRMA_CreateFrom_ID, l.Line,
((CASE WHEN l.QtyOrdered = 0 THEN 0 ELSE l.QtyEntered / l.QtyOrdered END) * (l.QtyOrdered - SUM(COALESCE(iol.MovementQty, 0)))) QtyEntered,
l.C_UOM_ID, (l.QtyOrdered - SUM(COALESCE(iol.MovementQty, 0))) MovementQty,
(CASE WHEN l.QtyOrdered = 0 THEN 0 ELSE l.QtyEntered / l.QtyOrdered END) Multiplier,
COALESCE(p.Name, c.Name) AS Name, l.M_Product_ID, l.M_AttributeSetInstance_ID, l.C_Charge_ID, l.Description, po.VendorProductNo,
-- Reference
o.C_Order_ID, NULL::NUMERIC(10, 0) AS C_Invoice_ID, NULL::NUMERIC(10, 0) AS M_InOut_ID, NULL::NUMERIC(10, 0) AS M_RMA_ID, o.DateOrdered AS DateDoc, o.C_BPartner_ID, o.DocStatus, p.Value AS ProductValue
FROM C_Order o
INNER JOIN C_OrderLine l ON(l.C_Order_ID = o.C_Order_ID)
LEFT JOIN LATERAL (
    SELECT ppo.VendorProductNo
    FROM M_Product_PO ppo
    WHERE ppo.M_Product_ID = l.M_Product_ID
      AND ppo.C_BPartner_ID = o.C_BPartner_ID
      AND ppo.C_Currency_ID = o.C_Currency_ID
      AND ppo.IsActive = 'Y'
      AND ppo.AD_Org_ID IN (0, o.AD_Org_ID)
    ORDER BY ppo.AD_Org_ID DESC LIMIT 1
) po ON true
LEFT JOIN M_Product p ON (l.M_Product_ID = p.M_Product_ID)
LEFT JOIN C_Charge c ON (l.C_Charge_ID = c.C_Charge_ID)
LEFT JOIN (SELECT iol_sub.C_OrderLine_ID, iol_sub.MovementQty
                FROM M_InOut io_sub
                INNER JOIN M_InOutLine iol_sub ON(iol_sub.M_InOut_ID = io_sub.M_InOut_ID)
                WHERE io_sub.DocStatus NOT IN('VO', 'IN', 'IP', 'RE', 'CL')
                AND iol_sub.C_OrderLine_ID IS NOT NULL) iol ON(iol.C_OrderLine_ID = l.C_OrderLine_ID)
WHERE l.QtyOrdered <> 0
GROUP BY l.AD_Client_ID, l.AD_Org_ID, l.CreatedBy, l.Created, l.UpdatedBy, l.Updated, l.IsActive, l.C_OrderLine_ID, l.Line,
l.QtyOrdered, l.QtyEntered, l.C_UOM_ID, p.Name, c.Name, l.M_Product_ID,
l.M_AttributeSetInstance_ID, l.C_Charge_ID, l.Description, po.VendorProductNo,
o.C_Order_ID, o.DateOrdered, o.C_BPartner_ID, o.DocStatus, p.Value
HAVING(l.QtyOrdered - SUM(COALESCE(iol.MovementQty, 0)) <> 0);