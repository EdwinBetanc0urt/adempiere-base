/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 *****************************************************************************/
package org.compiere.acct;

import org.adempiere.core.domains.models.I_C_LandedCost;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MConversionRate;
import org.compiere.model.MCostDetail;
import org.compiere.model.MCostType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLandedCost;
import org.compiere.model.MLandedCostAllocation;
import org.compiere.model.ProductCost;
import org.compiere.util.Env;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Doc_LandedCost extends Doc
{
	public Doc_LandedCost (MAcctSchema[] ass, ResultSet rs, String trxName)
	{
		super (ass, MLandedCost.class, rs, MLandedCost.DOCBASETYPE_LandedCost, trxName);
	}

	private MLandedCost landedCost = null;
	private List<MLandedCostAllocation> allocations = new ArrayList<>();

	public String loadDocumentDetails()
	{
		landedCost = (MLandedCost) getPO();
		setC_Currency_ID(landedCost.getC_Currency_ID());
		setDateDoc(landedCost.getDateDoc());
		setDateAcct(landedCost.getDateAcct());
		allocations = MLandedCostAllocation.getOfLandedCost(getCtx(), landedCost.get_ID(), getTrxName());
		p_lines = new DocLine[allocations.size()];
		for (int i = 0; i < allocations.size(); i++)
			p_lines[i] = new DocLine(allocations.get(i), this);
		return null;
	}

	public BigDecimal getBalance()
	{
		return Env.ZERO;
	}

	private static class Entry
	{
		DocLine line;
		int accountType;
		BigDecimal amt;
		String description;
		int productId;
	}

	public ArrayList<Fact> createFacts (MAcctSchema as)
	{
		Fact fact = new Fact(this, as, Fact.POST_Actual);
		setC_Currency_ID(as.getC_Currency_ID());
		setIsMultiCurrency(false);

		MInvoiceLine invoiceLine = new MInvoiceLine(getCtx(), landedCost.getC_InvoiceLine_ID(), getTrxName());
		MInvoice invoice = invoiceLine.getParent();

		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < allocations.size(); i++)
		{
			MLandedCostAllocation allocation = allocations.get(i);
			if (allocation.getBase().signum() == 0)
				continue;
			DocLine line = p_lines[i];
			MCostType costType = MCostType.get(as, allocation.getM_Product_ID(), allocation.getAD_Org_ID());
			BigDecimal costAllocation = allocation.getPriceActual().multiply(allocation.getQty());
			BigDecimal assetAmount = BigDecimal.ZERO;
			if (MCostType.COSTINGMETHOD_AverageInvoice.equals(costType.getCostingMethod()))
				assetAmount = Optional.ofNullable(MCostDetail.getByDocLineLandedCost(
					allocation, as.getC_AcctSchema_ID(), costType.get_ID())).orElse(BigDecimal.ZERO);
			String description = landedCost.getDocumentNo() + " " + allocation.getM_CostElement().getName();
			if (assetAmount.signum() != 0)
				entries.add(entry(line, ProductCost.ACCTTYPE_P_Asset, assetAmount, description, allocation.getM_Product_ID()));
			BigDecimal costAdjustment = costAllocation.subtract(assetAmount);
			if (costAdjustment.signum() != 0)
				entries.add(entry(line, ProductCost.ACCTTYPE_P_CostAdjustment, costAdjustment, description, allocation.getM_Product_ID()));
		}
		if (entries.isEmpty())
		{
			p_Error = "Resubmit - No Costs for " + getDocumentNo();
			return null;
		}

		BigDecimal clearingAmt = MConversionRate.convertBase(getCtx(), landedCost.getAmt(),
			landedCost.getC_Currency_ID(), invoice.getDateAcct(), landedCost.getC_ConversionType_ID(),
			getAD_Client_ID(), getAD_Org_ID());
		if (clearingAmt == null)
		{
			p_Error = "No Conversion Rate for " + getDocumentNo();
			return null;
		}
		BigDecimal debitTotal = BigDecimal.ZERO;
		for (Entry entry : entries)
			debitTotal = debitTotal.add(entry.amt);
		BigDecimal difference = clearingAmt.subtract(debitTotal);
		if (difference.signum() != 0)
		{
			Entry last = entries.get(entries.size() - 1);
			last.amt = last.amt.add(difference);
		}

		for (Entry entry : entries)
		{
			FactLine factLine = fact.createLine(entry.line,
				entry.line.getAccount(entry.accountType, as), as.getC_Currency_ID(),
				entry.amt.signum() >= 0 ? entry.amt : null,
				entry.amt.signum() < 0 ? entry.amt.negate() : null);
			if (factLine != null)
			{
				factLine.setDescription(entry.description);
				factLine.setM_Product_ID(entry.productId);
			}
		}

		ProductCost productCost = new ProductCost(getCtx(), invoiceLine.getM_Product_ID(), 0, getTrxName());
		FactLine clearingLine = fact.createLine(null,
			productCost.getAccount(ProductCost.ACCTTYPE_P_LandedCostClearing, as), as.getC_Currency_ID(),
			clearingAmt.signum() < 0 ? clearingAmt.negate() : null,
			clearingAmt.signum() >= 0 ? clearingAmt : null);
		if (clearingLine != null)
			clearingLine.setDescription(landedCost.getDocumentNo());

		ArrayList<Fact> facts = new ArrayList<>();
		facts.add(fact);
		return facts;
	}

	private Entry entry(DocLine line, int accountType, BigDecimal amt, String description, int productId)
	{
		Entry entry = new Entry();
		entry.line = line;
		entry.accountType = accountType;
		entry.amt = amt;
		entry.description = description;
		entry.productId = productId;
		return entry;
	}

	public static String getDateAcctColumnName()
	{
		return I_C_LandedCost.COLUMNNAME_DateAcct;
	}
}
