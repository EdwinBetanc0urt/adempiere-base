--DROP VIEW RV_Location;
CREATE OR REPLACE VIEW RV_Location AS
-- Address (C_Location) with its full address to display (Address 1, Address 2, Postal City, Region; empty parts left out),
-- the city and region resolved from C_City / C_Region and the columns to filter. SQL-99: PostgreSQL and Oracle.
SELECT l.C_Location_ID,
       l.AD_Client_ID,
       l.AD_Org_ID,
       l.IsActive,
       l.Created,
       l.CreatedBy,
       l.Updated,
       l.UpdatedBy,
       l.Address1,
       l.Address2,
       l.Postal,
       COALESCE(NULLIF(TRIM(l.City),''),ci.Name) AS City,
       l.C_City_ID,
       l.C_Region_ID,
       COALESCE(r.Name,NULLIF(TRIM(l.RegionName),'')) AS RegionName,
       l.C_Country_ID,
       co.Name AS CountryName,
       l.Latitude,
       l.Longitude,
       TRIM(TRIM(LEADING ',' FROM
          CASE WHEN NULLIF(TRIM(l.Address1),'') IS NULL THEN '' ELSE ', '||NULLIF(TRIM(l.Address1),'') END
          ||CASE WHEN NULLIF(TRIM(l.Address2),'') IS NULL THEN '' ELSE ', '||NULLIF(TRIM(l.Address2),'') END
          ||CASE WHEN NULLIF(TRIM(COALESCE(TRIM(l.Postal),'')||' '||COALESCE(COALESCE(NULLIF(TRIM(l.City),''),ci.Name),'')),'') IS NULL THEN '' ELSE ', '||NULLIF(TRIM(COALESCE(TRIM(l.Postal),'')||' '||COALESCE(COALESCE(NULLIF(TRIM(l.City),''),ci.Name),'')),'') END
          ||CASE WHEN COALESCE(r.Name,NULLIF(TRIM(l.RegionName),'')) IS NULL THEN '' ELSE ', '||COALESCE(r.Name,NULLIF(TRIM(l.RegionName),'')) END)) AS DisplayName
  FROM C_Location l
  LEFT JOIN C_City ci ON (ci.C_City_ID=l.C_City_ID)
  LEFT JOIN C_Region r ON (r.C_Region_ID=l.C_Region_ID)
  LEFT JOIN C_Country co ON (co.C_Country_ID=l.C_Country_ID);
