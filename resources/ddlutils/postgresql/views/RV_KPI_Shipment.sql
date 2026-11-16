CREATE OR REPLACE VIEW RV_KPI_Shipment
(RV_KPI_Shipment_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, Updated,
 M_InOut_ID, DocumentNo, POReference, Description, C_Order_ID,
 C_BPartner_ID, C_BP_Group_ID, C_BPartner_Location_ID, AD_User_ID,
 M_Warehouse_ID, M_Shipper_ID, SalesRep_ID, C_Project_ID, C_Campaign_ID, C_Activity_ID, AD_OrgTrx_ID, M_RMA_ID,
 TrackingNo, DeliveryRule, DeliveryViaRule, PriorityRule, FreightCostRule, IsInTransit,
 C_DocType_ID, DocBaseType, DocStatus, IsSOTrx, MovementType,
 MovementDate, DateAcct, DateOrdered, DatePromised,
 MovementQty, ShipmentCount, DaysToDeliver, DeliveryBucket, IsOnTime)
AS
SELECT x.M_InOut_ID,
    x.AD_Client_ID, x.AD_Org_ID, x.IsActive, x.Created, x.Updated,
    x.M_InOut_ID, x.DocumentNo, x.POReference, x.Description, x.C_Order_ID,
    x.C_BPartner_ID, x.C_BP_Group_ID, x.C_BPartner_Location_ID, x.AD_User_ID,
    x.M_Warehouse_ID, x.M_Shipper_ID, x.SalesRep_ID, x.C_Project_ID, x.C_Campaign_ID, x.C_Activity_ID, x.AD_OrgTrx_ID, x.M_RMA_ID,
    x.TrackingNo, x.DeliveryRule, x.DeliveryViaRule, x.PriorityRule, x.FreightCostRule, x.IsInTransit,
    x.C_DocType_ID, x.DocBaseType, x.DocStatus, x.IsSOTrx, x.MovementType,
    x.MovementDate, x.DateAcct, x.DateOrdered, x.DatePromised,
    x.MovementQty,
    1,
    CAST(x.DaysToDeliver AS INTEGER),
    CAST(CASE
        WHEN x.DaysToDeliver IS NULL THEN NULL
        WHEN x.DaysToDeliver < 2 THEN '<2'
        WHEN x.DaysToDeliver <= 5 THEN '2-5'
        ELSE '>5'
    END AS VARCHAR(3)),
    CASE
        WHEN x.DatePromised IS NULL THEN NULL
        WHEN CAST(x.MovementDate AS date) <= CAST(x.DatePromised AS date) THEN 1
        ELSE 0
    END
FROM (
    SELECT io.AD_Client_ID, io.AD_Org_ID, io.IsActive, io.Created, io.Updated,
        io.M_InOut_ID, io.DocumentNo, io.POReference, io.Description, io.C_Order_ID,
        io.C_BPartner_ID, bp.C_BP_Group_ID, io.C_BPartner_Location_ID, io.AD_User_ID,
        io.M_Warehouse_ID, io.M_Shipper_ID, io.SalesRep_ID, io.C_Project_ID, io.C_Campaign_ID, io.C_Activity_ID, io.AD_OrgTrx_ID, io.M_RMA_ID,
        io.TrackingNo, io.DeliveryRule, io.DeliveryViaRule, io.PriorityRule, io.FreightCostRule, io.IsInTransit,
        io.C_DocType_ID, dt.DocBaseType, io.DocStatus, io.IsSOTrx, io.MovementType,
        io.MovementDate, io.DateAcct, o.DateOrdered, o.DatePromised,
        COALESCE(l.MovementQty, 0) AS MovementQty,
        CASE
            WHEN o.DateOrdered IS NULL THEN NULL
            ELSE (
                SELECT COUNT(*)
                FROM generate_series(
                         CAST(CAST(o.DateOrdered AS date) AS timestamp),
                         CAST(CAST(io.MovementDate AS date) - 1 AS timestamp),
                         INTERVAL '1 day') AS d(dt)
                WHERE EXTRACT(ISODOW FROM d.dt) < 6
            )
        END AS DaysToDeliver
    FROM M_InOut io
        INNER JOIN C_DocType dt ON (dt.C_DocType_ID = io.C_DocType_ID)
        INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = io.C_BPartner_ID)
        LEFT JOIN C_Order o ON (o.C_Order_ID = io.C_Order_ID)
        LEFT JOIN (
            SELECT iol.M_InOut_ID, SUM(iol.MovementQty) AS MovementQty
            FROM M_InOutLine iol
            WHERE iol.IsActive = 'Y'
            GROUP BY iol.M_InOut_ID
        ) l ON (l.M_InOut_ID = io.M_InOut_ID)
) x;
