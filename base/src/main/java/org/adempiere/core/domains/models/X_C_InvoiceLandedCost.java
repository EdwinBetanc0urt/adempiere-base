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
/** Generated Model - DO NOT CHANGE */
package org.adempiere.core.domains.models;

import java.sql.Timestamp;
import org.compiere.util.Env;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;
import org.compiere.model.I_Persistent;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;
import org.compiere.util.KeyNamePair;

/** Generated Model for C_InvoiceLandedCost
 *  @author Adempiere (generated) 
 *  @version Release 3.9.4 - $Id$ */
public class X_C_InvoiceLandedCost extends PO implements I_C_InvoiceLandedCost, I_Persistent 
{

	/**
	 *
	 */
	private static final long serialVersionUID = 20261112L;

    /** Standard Constructor */
    public X_C_InvoiceLandedCost (Properties ctx, int C_InvoiceLandedCost_ID, String trxName)
    {
      super (ctx, C_InvoiceLandedCost_ID, trxName);
    }

    /** Load Constructor */
    public X_C_InvoiceLandedCost (Properties ctx, ResultSet rs, String trxName)
    {
      super (ctx, rs, trxName);
    }

    /** AccessLevel
      * @return 1 - Org 
      */
    protected int get_AccessLevel()
    {
      return accessLevel.intValue();
    }

    /** Load Meta Data */
    protected POInfo initPO (Properties ctx)
    {
      POInfo poi = POInfo.getPOInfo (ctx, Table_ID, get_TrxName());
      return poi;
    }

    public String toString()
    {
      StringBuffer sb = new StringBuffer ("X_C_InvoiceLandedCost[")
        .append(get_ID()).append("]");
      return sb.toString();
    }



	/** Set Invoice Landed Cost. */
	public void setC_InvoiceLandedCost_ID (int C_InvoiceLandedCost_ID)
	{
		if (C_InvoiceLandedCost_ID < 1) 
			set_ValueNoCheck (COLUMNNAME_C_InvoiceLandedCost_ID, null);
		else 
			set_ValueNoCheck (COLUMNNAME_C_InvoiceLandedCost_ID, Integer.valueOf(C_InvoiceLandedCost_ID));
	}

	/** Get Invoice Landed Cost. */
	public int getC_InvoiceLandedCost_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_InvoiceLandedCost_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Invoice. */
	public void setC_Invoice_ID (int C_Invoice_ID)
	{
		if (C_Invoice_ID < 1) 
			set_Value (COLUMNNAME_C_Invoice_ID, null);
		else 
			set_Value (COLUMNNAME_C_Invoice_ID, Integer.valueOf(C_Invoice_ID));
	}

	/** Get Invoice. */
	public int getC_Invoice_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_Invoice_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Invoice Line. */
	public void setC_InvoiceLine_ID (int C_InvoiceLine_ID)
	{
		if (C_InvoiceLine_ID < 1) 
			set_Value (COLUMNNAME_C_InvoiceLine_ID, null);
		else 
			set_Value (COLUMNNAME_C_InvoiceLine_ID, Integer.valueOf(C_InvoiceLine_ID));
	}

	/** Get Invoice Line. */
	public int getC_InvoiceLine_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_InvoiceLine_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Order. */
	public void setC_Order_ID (int C_Order_ID)
	{
		if (C_Order_ID < 1) 
			set_Value (COLUMNNAME_C_Order_ID, null);
		else 
			set_Value (COLUMNNAME_C_Order_ID, Integer.valueOf(C_Order_ID));
	}

	/** Get Order. */
	public int getC_Order_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_Order_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Order Line. */
	public void setC_OrderLine_ID (int C_OrderLine_ID)
	{
		if (C_OrderLine_ID < 1) 
			set_Value (COLUMNNAME_C_OrderLine_ID, null);
		else 
			set_Value (COLUMNNAME_C_OrderLine_ID, Integer.valueOf(C_OrderLine_ID));
	}

	/** Get Order Line. */
	public int getC_OrderLine_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_OrderLine_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Landed Cost Type. */
	public void setC_LandedCostType_ID (int C_LandedCostType_ID)
	{
		if (C_LandedCostType_ID < 1) 
			set_Value (COLUMNNAME_C_LandedCostType_ID, null);
		else 
			set_Value (COLUMNNAME_C_LandedCostType_ID, Integer.valueOf(C_LandedCostType_ID));
	}

	/** Get Landed Cost Type. */
	public int getC_LandedCostType_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_LandedCostType_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Description. */
	public void setDescription (String Description)
	{
		set_Value (COLUMNNAME_Description, Description);
	}

	/** Get Description. */
	public String getDescription () 
	{
		return (String)get_Value(COLUMNNAME_Description);
	}

	/** Set Generated. */
	public void setIsGenerated (boolean IsGenerated)
	{
		set_Value (COLUMNNAME_IsGenerated, Boolean.valueOf(IsGenerated));
	}

	/** Get Generated. */
	public boolean isGenerated () 
	{
		Object oo = get_Value(COLUMNNAME_IsGenerated);
		if (oo != null) 
		{
			 if (oo instanceof Boolean) 
				 return ((Boolean)oo).booleanValue(); 
			return "Y".equals(oo);
		}
		return false;
	}

	/** Set Preliminary. */
	public void setIsPreliminary (boolean IsPreliminary)
	{
		set_Value (COLUMNNAME_IsPreliminary, Boolean.valueOf(IsPreliminary));
	}

	/** Get Preliminary. */
	public boolean isPreliminary () 
	{
		Object oo = get_Value(COLUMNNAME_IsPreliminary);
		if (oo != null) 
		{
			 if (oo instanceof Boolean) 
				 return ((Boolean)oo).booleanValue(); 
			return "Y".equals(oo);
		}
		return false;
	}

	/** LandedCostDistribution AD_Reference_ID=339 */
	public static final int LANDEDCOSTDISTRIBUTION_AD_Reference_ID=339;
	/** Quantity = Q */
	public static final String LANDEDCOSTDISTRIBUTION_Quantity = "Q";
	/** Line = L */
	public static final String LANDEDCOSTDISTRIBUTION_Line = "L";
	/** Volume = V */
	public static final String LANDEDCOSTDISTRIBUTION_Volume = "V";
	/** Weight = W */
	public static final String LANDEDCOSTDISTRIBUTION_Weight = "W";
	/** Costs = C */
	public static final String LANDEDCOSTDISTRIBUTION_Costs = "C";

	/** Set Cost Distribution. */
	public void setLandedCostDistribution (String LandedCostDistribution)
	{
		set_Value (COLUMNNAME_LandedCostDistribution, LandedCostDistribution);
	}

	/** Get Cost Distribution. */
	public String getLandedCostDistribution () 
	{
		return (String)get_Value(COLUMNNAME_LandedCostDistribution);
	}

	/** Set Cost Element. */
	public void setM_CostElement_ID (int M_CostElement_ID)
	{
		if (M_CostElement_ID < 1) 
			set_Value (COLUMNNAME_M_CostElement_ID, null);
		else 
			set_Value (COLUMNNAME_M_CostElement_ID, Integer.valueOf(M_CostElement_ID));
	}

	/** Get Cost Element. */
	public int getM_CostElement_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_M_CostElement_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Shipment/Receipt. */
	public void setM_InOut_ID (int M_InOut_ID)
	{
		if (M_InOut_ID < 1) 
			set_Value (COLUMNNAME_M_InOut_ID, null);
		else 
			set_Value (COLUMNNAME_M_InOut_ID, Integer.valueOf(M_InOut_ID));
	}

	/** Get Shipment/Receipt. */
	public int getM_InOut_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_M_InOut_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Shipment/Receipt Line. */
	public void setM_InOutLine_ID (int M_InOutLine_ID)
	{
		if (M_InOutLine_ID < 1) 
			set_Value (COLUMNNAME_M_InOutLine_ID, null);
		else 
			set_Value (COLUMNNAME_M_InOutLine_ID, Integer.valueOf(M_InOutLine_ID));
	}

	/** Get Shipment/Receipt Line. */
	public int getM_InOutLine_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_M_InOutLine_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Product. */
	public void setM_Product_ID (int M_Product_ID)
	{
		if (M_Product_ID < 1) 
			set_Value (COLUMNNAME_M_Product_ID, null);
		else 
			set_Value (COLUMNNAME_M_Product_ID, Integer.valueOf(M_Product_ID));
	}

	/** Get Product. */
	public int getM_Product_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_M_Product_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}



	/** Set Immutable Universally Unique Identifier. */
	public void setUUID (String UUID)
	{
		set_Value (COLUMNNAME_UUID, UUID);
	}

	/** Get Immutable Universally Unique Identifier. */
	public String getUUID () 
	{
		return (String)get_Value(COLUMNNAME_UUID);
	}
}
