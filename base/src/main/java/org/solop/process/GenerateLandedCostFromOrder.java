/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 *****************************************************************************/
package org.solop.process;

import org.adempiere.core.domains.models.I_C_OrderLine;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MCostElement;
import org.compiere.model.MInvoiceLandedCost;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.Query;
import org.compiere.process.SvrProcess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Create the landed cost distribution drafts of an invoice line against the purchase orders
 * or purchase order lines selected in the purchase orders browser.
 */
public class GenerateLandedCostFromOrder extends SvrProcess {

	private int costElementId;
	private String landedCostDistribution;
	private boolean isCreateByProduct;
	private int landedCostTypeId;
	private MInvoiceLine invoiceLine;

	@Override
	protected void prepare() {
		costElementId = getParameterAsInt("M_CostElement_ID");
		landedCostDistribution = getParameterAsString("LandedCostDistribution");
		isCreateByProduct = getParameterAsBoolean("IsCreateByProduct");
		landedCostTypeId = getParameterAsInt("C_LandedCostType_ID");
		if (getRecord_ID() <= 0) {
			throw new AdempiereException("@C_InvoiceLine_ID@ @NotFound@");
		}
		invoiceLine = new MInvoiceLine(getCtx(), getRecord_ID(), get_TrxName());
		if (invoiceLine.getM_Product_ID() > 0) {
			MProduct product = (MProduct) invoiceLine.getM_Product();
			if (product.isStocked() || MProduct.PRODUCTTYPE_Item.equals(product.getProductType())) {
				throw new AdempiereException("InvalidLandedCostAllocation");
			}
		}
		MCostElement costElement = MCostElement.get(getCtx(), costElementId);
		if (MCostElement.COSTELEMENTTYPE_Material.equals(costElement.getCostElementType())) {
			throw new AdempiereException("InvalidMaterialLandedCost");
		}
	}

	@Override
	protected String doIt() throws Exception {
		List<MOrderLine> orderLines = getSelectedOrderLines();
		if (orderLines.isEmpty()) {
			return "@NotFound@ @C_OrderLine_ID@";
		}
		StringBuilder orders = new StringBuilder();
		if (isCreateByProduct) {
			for (MOrderLine orderLine : orderLines) {
				createDraft(orderLine.getParent(), orderLine);
				orders.append(orderLine.getParent().getDocumentNo()).append(" ").append(orderLine.getLine()).append(" ");
			}
		} else {
			Map<Integer, MOrder> byOrder = new LinkedHashMap<>();
			for (MOrderLine orderLine : orderLines) {
				byOrder.putIfAbsent(orderLine.getC_Order_ID(), orderLine.getParent());
			}
			for (MOrder order : byOrder.values()) {
				createDraft(order, null);
				orders.append(order.getDocumentNo()).append(" ");
			}
		}
		return orders.toString().trim();
	}

	private void createDraft(MOrder order, MOrderLine orderLine) {
		MInvoiceLandedCost draft = new MInvoiceLandedCost(getCtx(), 0, get_TrxName());
		draft.setAD_Org_ID(order.getAD_Org_ID());
		draft.setC_InvoiceLine_ID(invoiceLine.getC_InvoiceLine_ID());
		draft.setC_Invoice_ID(invoiceLine.getC_Invoice_ID());
		draft.setC_Order_ID(order.getC_Order_ID());
		if (orderLine != null) {
			draft.setC_OrderLine_ID(orderLine.getC_OrderLine_ID());
			draft.setM_Product_ID(orderLine.getM_Product_ID());
		}
		draft.setDescription(order.getPOReference());
		draft.setLandedCostDistribution(landedCostDistribution);
		draft.setM_CostElement_ID(costElementId);
		draft.setC_LandedCostType_ID(landedCostTypeId);
		draft.saveEx();
	}

	private List<MOrderLine> getSelectedOrderLines() {
		StringBuilder whereClause = new StringBuilder(
			"EXISTS (SELECT 1 FROM T_Selection s WHERE s.AD_PInstance_ID=? AND s.T_Selection_ID=C_OrderLine.C_OrderLine_ID) "
				+ "AND EXISTS (SELECT 1 FROM C_Order o WHERE o.C_Order_ID=C_OrderLine.C_Order_ID AND o.IsSOTrx='N') ");
		if (isCreateByProduct) {
			whereClause.append("AND NOT EXISTS (SELECT 1 FROM C_InvoiceLandedCost d WHERE d.C_InvoiceLine_ID=? AND d.C_OrderLine_ID=C_OrderLine.C_OrderLine_ID)");
		} else {
			whereClause.append("AND NOT EXISTS (SELECT 1 FROM C_InvoiceLandedCost d WHERE d.C_InvoiceLine_ID=? AND d.C_Order_ID=C_OrderLine.C_Order_ID AND d.C_OrderLine_ID IS NULL)");
		}
		return new Query(getCtx(), I_C_OrderLine.Table_Name, whereClause.toString(), get_TrxName())
			.setClient_ID()
			.setParameters(getAD_PInstance_ID(), getRecord_ID())
			.setOrderBy("C_Order_ID, Line")
			.list();
	}
}
