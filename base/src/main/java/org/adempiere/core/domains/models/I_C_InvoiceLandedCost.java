/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.                                     *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net                                                  *
 * or https://github.com/adempiere/adempiere/blob/develop/license.html        *
 *****************************************************************************/
package org.adempiere.core.domains.models;

import java.math.BigDecimal;
import java.sql.Timestamp;
import org.compiere.model.MTable;
import org.compiere.util.KeyNamePair;

/** Generated Interface for C_InvoiceLandedCost
 *  @author Adempiere (generated) 
 *  @version Release 3.9.4
 */
public interface I_C_InvoiceLandedCost 
{

    /** TableName=C_InvoiceLandedCost */
    public static final String Table_Name = "C_InvoiceLandedCost";

    /** AD_Table_ID=0 */
    public static final int Table_ID = MTable.getTable_ID(Table_Name);

    KeyNamePair Model = new KeyNamePair(Table_ID, Table_Name);

    /** AccessLevel = 1 - Org 
     */
    BigDecimal accessLevel = BigDecimal.valueOf(1);

    /** Load Meta Data */

    /** Column name AD_Client_ID */
    public static final String COLUMNNAME_AD_Client_ID = "AD_Client_ID";

	/** Get Client. */
	public int getAD_Client_ID();

    /** Column name AD_Org_ID */
    public static final String COLUMNNAME_AD_Org_ID = "AD_Org_ID";


	/** Get Organization. */
	public int getAD_Org_ID();

    /** Column name C_InvoiceLandedCost_ID */
    public static final String COLUMNNAME_C_InvoiceLandedCost_ID = "C_InvoiceLandedCost_ID";

	/** Set Invoice Landed Cost. */
	public void setC_InvoiceLandedCost_ID (int C_InvoiceLandedCost_ID);

	/** Get Invoice Landed Cost. */
	public int getC_InvoiceLandedCost_ID();

    /** Column name C_Invoice_ID */
    public static final String COLUMNNAME_C_Invoice_ID = "C_Invoice_ID";

	/** Set Invoice. */
	public void setC_Invoice_ID (int C_Invoice_ID);

	/** Get Invoice. */
	public int getC_Invoice_ID();

    /** Column name C_InvoiceLine_ID */
    public static final String COLUMNNAME_C_InvoiceLine_ID = "C_InvoiceLine_ID";

	/** Set Invoice Line. */
	public void setC_InvoiceLine_ID (int C_InvoiceLine_ID);

	/** Get Invoice Line. */
	public int getC_InvoiceLine_ID();

    /** Column name C_Order_ID */
    public static final String COLUMNNAME_C_Order_ID = "C_Order_ID";

	/** Set Order. */
	public void setC_Order_ID (int C_Order_ID);

	/** Get Order. */
	public int getC_Order_ID();

    /** Column name C_OrderLine_ID */
    public static final String COLUMNNAME_C_OrderLine_ID = "C_OrderLine_ID";

	/** Set Order Line. */
	public void setC_OrderLine_ID (int C_OrderLine_ID);

	/** Get Order Line. */
	public int getC_OrderLine_ID();

    /** Column name C_LandedCostType_ID */
    public static final String COLUMNNAME_C_LandedCostType_ID = "C_LandedCostType_ID";

	/** Set Landed Cost Type. */
	public void setC_LandedCostType_ID (int C_LandedCostType_ID);

	/** Get Landed Cost Type. */
	public int getC_LandedCostType_ID();

    /** Column name Description */
    public static final String COLUMNNAME_Description = "Description";

	/** Set Description. */
	public void setDescription (String Description);

	/** Get Description. */
	public String getDescription();

    /** Column name IsGenerated */
    public static final String COLUMNNAME_IsGenerated = "IsGenerated";

	/** Set Generated. */
	public void setIsGenerated (boolean IsGenerated);

	/** Get Generated. */
	public boolean isGenerated();

    /** Column name IsPreliminary */
    public static final String COLUMNNAME_IsPreliminary = "IsPreliminary";

	/** Set Preliminary. */
	public void setIsPreliminary (boolean IsPreliminary);

	/** Get Preliminary. */
	public boolean isPreliminary();

    /** Column name LandedCostDistribution */
    public static final String COLUMNNAME_LandedCostDistribution = "LandedCostDistribution";

	/** Set Cost Distribution. */
	public void setLandedCostDistribution (String LandedCostDistribution);

	/** Get Cost Distribution. */
	public String getLandedCostDistribution();

    /** Column name M_CostElement_ID */
    public static final String COLUMNNAME_M_CostElement_ID = "M_CostElement_ID";

	/** Set Cost Element. */
	public void setM_CostElement_ID (int M_CostElement_ID);

	/** Get Cost Element. */
	public int getM_CostElement_ID();

    /** Column name M_InOut_ID */
    public static final String COLUMNNAME_M_InOut_ID = "M_InOut_ID";

	/** Set Shipment/Receipt. */
	public void setM_InOut_ID (int M_InOut_ID);

	/** Get Shipment/Receipt. */
	public int getM_InOut_ID();

    /** Column name M_InOutLine_ID */
    public static final String COLUMNNAME_M_InOutLine_ID = "M_InOutLine_ID";

	/** Set Shipment/Receipt Line. */
	public void setM_InOutLine_ID (int M_InOutLine_ID);

	/** Get Shipment/Receipt Line. */
	public int getM_InOutLine_ID();

    /** Column name M_Product_ID */
    public static final String COLUMNNAME_M_Product_ID = "M_Product_ID";

	/** Set Product. */
	public void setM_Product_ID (int M_Product_ID);

	/** Get Product. */
	public int getM_Product_ID();


    /** Column name UUID */
    public static final String COLUMNNAME_UUID = "UUID";

	/** Set Immutable Universally Unique Identifier. */
	public void setUUID (String UUID);

	/** Get Immutable Universally Unique Identifier. */
	public String getUUID();

    /** Column name Created */
    public static final String COLUMNNAME_Created = "Created";

	public Timestamp getCreated();

    /** Column name CreatedBy */
    public static final String COLUMNNAME_CreatedBy = "CreatedBy";

	public int getCreatedBy();

    /** Column name Updated */
    public static final String COLUMNNAME_Updated = "Updated";

	public Timestamp getUpdated();

    /** Column name UpdatedBy */
    public static final String COLUMNNAME_UpdatedBy = "UpdatedBy";

	public int getUpdatedBy();
}
