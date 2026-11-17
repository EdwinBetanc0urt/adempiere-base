/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import org.adempiere.core.domains.models.I_C_LandedCostAllocation;
import org.adempiere.core.domains.models.X_C_LandedCostAllocation;
import org.adempiere.engine.IDocumentLine;
import org.compiere.util.DB;
import org.compiere.util.Env;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 	Landed Cost Allocation Model
 *  @author Jorg Janke
 *  @version $Id: MLandedCostAllocation.java,v 1.3 2006/07/30 00:51:05 jjanke Exp $
 */
public class MLandedCostAllocation extends X_C_LandedCostAllocation implements IDocumentLine
{	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8645283018475474574L;


	/**
	 * 	Get Cost Allocations for invoice Line
	 *	@param ctx context
	 *	@param C_InvoiceLine_ID invoice line
	 *	@param trxName trx
	 *	@return landed cost alloc
	 */
	public static MLandedCostAllocation[] getOfInvoiceLine (Properties ctx,
		int C_InvoiceLine_ID, String trxName)
	{
		List<MLandedCostAllocation> list = new Query(ctx, MLandedCostAllocation.Table_Name,
				I_C_LandedCostAllocation.COLUMNNAME_C_InvoiceLine_ID + "=?", trxName)
			.setParameters(C_InvoiceLine_ID)
			.list();
		MLandedCostAllocation[] retValue = new MLandedCostAllocation[list.size ()];
		list.toArray (retValue);
		return retValue;
	}	//	getOfInvliceLine
	
	/**
	 * Get Landed Cost Allocation
	 * @param ioLine InOutLine
	 * @return List MLandedCostAllocation
	 */
	public static List<MLandedCostAllocation> getOfInOutline(MInOutLine ioLine)
	{
		return getOfInOutline(ioLine, 0);
	}	//	getOfInvliceLine
	
	/**
	 * Get Landed Cost Allocation
	 * @param ioLine InOutLine
	 * @param costElementId
	 * @return List MLandedCostAllocation
	 */
	public static List<MLandedCostAllocation> getOfInOutline(MInOutLine ioLine, int costElementId)
	{
		StringBuilder whereClause = new StringBuilder();
		List<Object> parameters = new ArrayList<Object>();
		whereClause.append(I_C_LandedCostAllocation.COLUMNNAME_M_InOutLine_ID).append("=? ");
		whereClause.append(" AND ((C_LandedCostAllocation.C_LandedCost_ID IS NOT NULL AND EXISTS(SELECT 1 FROM C_LandedCost lc WHERE lc.C_LandedCost_ID = C_LandedCostAllocation.C_LandedCost_ID AND lc.Processed = 'Y'))"
				+ " OR (C_LandedCostAllocation.C_LandedCost_ID IS NULL AND EXISTS(SELECT 1 FROM C_InvoiceLine il WHERE il.C_InvoiceLine_ID = C_LandedCostAllocation.C_InvoiceLine_ID AND il.Processed = 'Y')))");
		parameters.add(ioLine.getM_InOutLine_ID());
		if (costElementId > 0 ) {
			whereClause.append(" AND ").append(I_C_LandedCostAllocation.COLUMNNAME_M_CostElement_ID).append("=? ");
			parameters.add(costElementId);
		}
		return new Query(ioLine.getCtx(),MLandedCostAllocation.Table_Name, whereClause.toString() , ioLine.get_TrxName())
		.setClient_ID()
		.setParameters(parameters)
		.list();
	}	//	getOfInvliceLine
	public static List<MLandedCostAllocation> getOfLandedCost(Properties ctx, int C_LandedCost_ID, String trxName)
	{
		return new Query(ctx, MLandedCostAllocation.Table_Name,
				I_C_LandedCostAllocation.COLUMNNAME_C_LandedCost_ID + "=?", trxName)
			.setParameters(C_LandedCost_ID)
			.list();
	}

	/***************************************************************************
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param C_LandedCostAllocation_ID id
	 *	@param trxName trx
	 */
	public MLandedCostAllocation(Properties ctx, int C_LandedCostAllocation_ID, String trxName)
	{
		super (ctx, C_LandedCostAllocation_ID, trxName);
		if (C_LandedCostAllocation_ID == 0)
		{
		//	setM_CostElement_ID(0);
			setAmt (Env.ZERO);
			setQty (Env.ZERO);
			setBase (Env.ZERO);
		}
	}	//	MLandedCostAllocation

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result name
	 *	@param trxName trx
	 */
	public MLandedCostAllocation(Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}	//	MLandedCostAllocation

	
	/**
	 * 	Parent Constructor
	 *	@param parent parent
	 *	@param M_CostElement_ID cost element
	 */
	public MLandedCostAllocation(MLandedCost parent, int M_CostElement_ID)
	{
		this (parent.getCtx(), 0, parent.get_TrxName());
		setClientOrg(parent);
		setC_LandedCost_ID(parent.getC_LandedCost_ID());
		setC_InvoiceLine_ID(parent.getC_InvoiceLine_ID());
		setM_CostElement_ID(M_CostElement_ID);
	}	//	MLandedCostAllocation

	private MLandedCost getLandedCost()
	{
		if (getC_LandedCost_ID() <= 0)
			return null;
		return new MLandedCost(getCtx(), getC_LandedCost_ID(), get_TrxName());
	}

	public Timestamp getConversionDate()
	{
		return getC_InvoiceLine().getC_Invoice().getDateAcct();
	}

	/**
	 * 	Set Amt
	 *	@param Amt amount
	 *	@param precision precision
	 */
	public void setAmt (BigDecimal bd, int precision)
	{
		if (bd.scale() > precision) {
			bd = bd.setScale(precision, RoundingMode.HALF_UP);
		}
		super.setAmt(bd);
	}	//	setAmt

	/**
	 * 	Set Allocation Qty (e.g. free products)
	 *	@param Qty
	 */
	public void setQty (BigDecimal Qty)
	{
		super.setQty (Qty);
	}	//	setQty

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getM_Locator_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override //ancabradau
	public BigDecimal getMovementQty()
	{
		
		return this.getQty();
	}

	@Override //ancabradau
	public BigDecimal getPriceActual()
	{
		if (getQty().signum() == 0)
			return Env.ZERO;
		MCurrency currency = MCurrency.get(getCtx(), getC_Currency_ID());
		BigDecimal amount = MConversionRate.convertBase(getCtx() , getAmt() , getC_Currency_ID() , getConversionDate() , getC_ConversionType_ID() , getAD_Client_ID() , getAD_Org_ID());
		BigDecimal price = amount.divide(getQty(), currency.getCostingPrecision() ,  RoundingMode.HALF_UP);
		return price;
	}

	@Override
	public int getReversalLine_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isSOTrx() {
		return false;
	}

	@Override
	public void setM_Locator_ID(int M_Locator_ID) {
		// TODO Auto-generated method stub
		
	}
	
	public Timestamp getDateAcct() {
		MLandedCost landedCost = getLandedCost();
		if (landedCost != null)
			return landedCost.getDateAcct();
		return getC_InvoiceLine().getC_Invoice().getDateAcct();
	}
	

	public IDocumentLine getReversalDocumentLine() {
		return null;
	}

	@Override
	public int getM_AttributeSetInstanceTo_ID() {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int getM_LocatorTo_ID() {
		// TODO Auto-generated method stub
		return -1;
	}
	
	@Override
	public int getC_DocType_ID() {
		return -1;
	}

	public BigDecimal getPriceActualCurrency() {
		if (getQty().signum() == 0)
			return Env.ZERO;
		BigDecimal amount = getAmt().divide(getQty() , MathContext.DECIMAL128);
		return  amount;
	}

	@Override
	public int getC_Currency_ID ()
	{
		MLandedCost landedCost = getLandedCost();
		if (landedCost != null && landedCost.getC_Currency_ID() > 0)
			return landedCost.getC_Currency_ID();
		return DB.getSQLValue(get_TrxName() ,
				"SELECT i.C_Currency_ID FROM C_InvoiceLine il INNER JOIN C_Invoice i ON (il.C_Invoice_ID=i.C_Invoice_ID) WHERE il.C_InvoiceLine_ID = ? ",
				getC_InvoiceLine_ID());
	}

	@Override
	public int getC_ConversionType_ID()
	{
		MLandedCost landedCost = getLandedCost();
		if (landedCost != null && landedCost.getC_ConversionType_ID() > 0)
			return landedCost.getC_ConversionType_ID();
		return DB.getSQLValue(get_TrxName() ,
				"SELECT i.C_ConversionType_ID FROM C_InvoiceLine il INNER JOIN C_Invoice i ON (il.C_Invoice_ID=i.C_Invoice_ID) WHERE il.C_InvoiceLine_ID = ? ",
				getC_InvoiceLine_ID());
	}

	@Override
	public boolean isReversalParent() {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public BigDecimal getAmt() {
		return getAmt(false);
	}

	public BigDecimal getAmt(boolean raw) {
		BigDecimal amount = super.getAmt();
		if (raw || getC_LandedCost_ID() > 0)
			return amount;
		MInvoiceLine invoiceLine = (MInvoiceLine) getC_InvoiceLine();
		if (MDocType.DOCBASETYPE_APCreditMemo.equals(invoiceLine.getParent().getC_DocTypeTarget().getDocBaseType()))
			amount = amount.negate();
		return  amount;
	}

}	//	MLandedCostAllocation
