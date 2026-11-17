/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 *****************************************************************************/
package org.compiere.process;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInOut;
import org.compiere.model.MInvoiceLandedCost;
import org.compiere.model.MLandedCost;
import org.compiere.util.Msg;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 	Generate the Landed Cost documents of a landed cost draft of the invoice
 */
public class LandedCostGenerate extends SvrProcess
{
	private Timestamp dateAcct = null;

	protected void prepare()
	{
		for (ProcessInfoParameter para : getParameter())
		{
			if (para.getParameter() == null)
				continue;
			if ("DateAcct".equals(para.getParameterName()))
				dateAcct = (Timestamp) para.getParameter();
		}
	}

	protected String doIt() throws Exception
	{
		MInvoiceLandedCost draft = new MInvoiceLandedCost(getCtx(), getRecord_ID(), get_TrxName());
		if (draft.getC_InvoiceLandedCost_ID() == 0)
			throw new AdempiereException("@C_InvoiceLandedCost_ID@ @NotFound@");
		if (draft.isGenerated())
			throw new AdempiereException("@Processed@");
		List<MLandedCost> created = new ArrayList<>();
		if (draft.isPreliminary())
		{
			for (MInOut receipt : getReceipts(draft))
			{
				MLandedCost landedCost = MInvoiceLandedCost.generateForReceipt(draft, receipt, dateAcct);
				if (landedCost != null)
					created.add(landedCost);
			}
		}
		else
		{
			created.addAll(MInvoiceLandedCost.generateForLine(draft.getInvoiceLine(), draft, dateAcct));
		}
		StringBuilder result = new StringBuilder();
		for (MLandedCost landedCost : created)
		{
			result.append(landedCost.getDocumentNo()).append(" ");
			addLog(landedCost.get_ID(), null, landedCost.getAmt(), landedCost.getDocumentNo());
		}
		if (created.isEmpty())
			return Msg.getMsg(getCtx(), "LandedCostNoMatchError");
		return result.toString().trim();
	}

	private List<MInOut> getReceipts(MInvoiceLandedCost draft)
	{
		String where = "IsSOTrx = 'N' AND DocStatus IN ('CO','CL') AND Reversal_ID IS NULL "
			+ "AND EXISTS(SELECT 1 FROM M_InOutLine iol INNER JOIN C_OrderLine ol ON (ol.C_OrderLine_ID = iol.C_OrderLine_ID) "
			+ "WHERE iol.M_InOut_ID = M_InOut.M_InOut_ID AND ol.C_Order_ID = ?)";
		return new org.compiere.model.Query(getCtx(), MInOut.Table_Name, where, get_TrxName())
			.setParameters(draft.getScopeOrderId())
			.setOrderBy("DateAcct, M_InOut_ID")
			.list();
	}
}
