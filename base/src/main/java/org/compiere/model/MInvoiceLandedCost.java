/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 *****************************************************************************/
package org.compiere.model;

import org.adempiere.core.domains.models.X_C_InvoiceLandedCost;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;
import org.compiere.util.Env;
import org.compiere.util.Msg;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

public class MInvoiceLandedCost extends X_C_InvoiceLandedCost {

	private static final long serialVersionUID = 20261112L;

	public MInvoiceLandedCost(Properties ctx, int C_InvoiceLandedCost_ID, String trxName) {
		super(ctx, C_InvoiceLandedCost_ID, trxName);
		if (C_InvoiceLandedCost_ID == 0) {
			setLandedCostDistribution(LANDEDCOSTDISTRIBUTION_Quantity);
			setIsGenerated(false);
			setIsPreliminary(false);
		}
	}

	public MInvoiceLandedCost(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	public static List<MInvoiceLandedCost> getOfInvoiceLine(Properties ctx, int invoiceLineId, String trxName) {
		return new Query(ctx, Table_Name, COLUMNNAME_C_InvoiceLine_ID + "=?", trxName)
				.setParameters(invoiceLineId)
				.setOnlyActiveRecords(true)
				.setOrderBy(COLUMNNAME_C_InvoiceLandedCost_ID)
				.list();
	}

	public static List<MInvoiceLandedCost> getOfInvoice(Properties ctx, int invoiceId, String trxName) {
		return new Query(ctx, Table_Name, COLUMNNAME_C_Invoice_ID + "=?", trxName)
				.setParameters(invoiceId)
				.setOnlyActiveRecords(true)
				.setOrderBy(COLUMNNAME_C_InvoiceLandedCost_ID)
				.list();
	}

	public static List<MInvoiceLandedCost> getPendingOfOrder(Properties ctx, int orderId, String trxName) {
		String where = "IsPreliminary = 'Y' AND IsGenerated = 'N' "
				+ "AND (C_Order_ID = ? OR EXISTS(SELECT 1 FROM C_OrderLine ol WHERE ol.C_OrderLine_ID = C_InvoiceLandedCost.C_OrderLine_ID AND ol.C_Order_ID = ?)) "
				+ "AND EXISTS(SELECT 1 FROM C_Invoice i WHERE i.C_Invoice_ID = C_InvoiceLandedCost.C_Invoice_ID AND i.DocStatus IN ('CO','CL'))";
		return new Query(ctx, Table_Name, where, trxName)
				.setParameters(orderId, orderId)
				.setOnlyActiveRecords(true)
				.setOrderBy(COLUMNNAME_C_InvoiceLandedCost_ID)
				.list();
	}

	public static String validate(MInvoice invoice) {
		LinkedHashSet<String> issues = new LinkedHashSet<>();
		for (MInvoiceLine line : invoice.getLines()) {
			List<MInvoiceLandedCost> drafts = getOfInvoiceLine(invoice.getCtx(), line.getC_InvoiceLine_ID(), invoice.get_TrxName());
			if (drafts.size() < 2)
				continue;
			MInvoiceLandedCost first = drafts.get(0);
			for (MInvoiceLandedCost draft : drafts) {
				if (!first.getLandedCostDistribution().equals(draft.getLandedCostDistribution()))
					issues.add(Msg.getMsg(invoice.getCtx(), "LandedCostInconsistentDist"));
				if (first.getM_CostElement_ID() != draft.getM_CostElement_ID())
					issues.add(Msg.getMsg(invoice.getCtx(), "LandedCostDiffElementErr"));
				if (first.isPreliminary() != draft.isPreliminary())
					issues.add(Msg.getMsg(invoice.getCtx(), "LandedCostMixedDraftError"));
			}
		}
		if (issues.isEmpty())
			return null;
		return String.join("\n", issues);
	}

	protected boolean beforeSave(boolean newRecord) {
		if (!newRecord && isGenerated() && !is_ValueChanged(COLUMNNAME_IsGenerated)) {
			log.saveError("Error", Msg.parseTranslation(getCtx(), "@Processed@"));
			return false;
		}
		if (getM_Product_ID() == 0 && getM_InOut_ID() == 0 && getM_InOutLine_ID() == 0
				&& getC_Order_ID() == 0 && getC_OrderLine_ID() == 0) {
			log.saveError("Error", Msg.parseTranslation(getCtx(),
					"@NotFound@ @M_Product_ID@ | @M_InOut_ID@ | @M_InOutLine_ID@ | @C_Order_ID@ | @C_OrderLine_ID@"));
			return false;
		}
		if (getM_InOutLine_ID() != 0 && getM_Product_ID() != 0)
			setM_Product_ID(0);
		setIsPreliminary(getC_Order_ID() != 0 || getC_OrderLine_ID() != 0);
		if (getC_Invoice_ID() == 0 && getC_InvoiceLine_ID() != 0)
			setC_Invoice_ID(getInvoiceLine().getC_Invoice_ID());
		return true;
	}

	public MInvoiceLine getInvoiceLine() {
		return new MInvoiceLine(getCtx(), getC_InvoiceLine_ID(), get_TrxName());
	}

	public int getScopeOrderId() {
		if (getC_Order_ID() != 0)
			return getC_Order_ID();
		if (getC_OrderLine_ID() != 0)
			return new MOrderLine(getCtx(), getC_OrderLine_ID(), get_TrxName()).getC_Order_ID();
		return 0;
	}

	public List<MOrderLine> getScopeOrderLines() {
		List<MOrderLine> list = new ArrayList<>();
		if (getC_OrderLine_ID() != 0) {
			list.add(new MOrderLine(getCtx(), getC_OrderLine_ID(), get_TrxName()));
			return list;
		}
		for (MOrderLine ol : new MOrder(getCtx(), getScopeOrderId(), get_TrxName()).getLines()) {
			if (ol.getM_Product_ID() == 0)
				continue;
			if (getM_Product_ID() != 0 && getM_Product_ID() != ol.getM_Product_ID())
				continue;
			list.add(ol);
		}
		return list;
	}

	public List<MInOutLine> getScopeReceiptLines() {
		List<MInOutLine> list = new ArrayList<>();
		if (getM_InOutLine_ID() != 0) {
			MInOutLine iol = new MInOutLine(getCtx(), getM_InOutLine_ID(), get_TrxName());
			if (!iol.isDescription() && iol.getM_Product_ID() != 0)
				list.add(iol);
			return list;
		}
		if (getM_InOut_ID() != 0) {
			for (MInOutLine iol : new MInOut(getCtx(), getM_InOut_ID(), get_TrxName()).getLines()) {
				if (iol.isDescription() || iol.getM_Product_ID() == 0)
					continue;
				if (getM_Product_ID() != 0 && getM_Product_ID() != iol.getM_Product_ID())
					continue;
				list.add(iol);
			}
		}
		return list;
	}

	public static BigDecimal getOrderBase(String distribution, MOrderLine orderLine, BigDecimal qty) {
		if (qty == null || qty.signum() == 0)
			return Env.ZERO;
		MProduct product = orderLine.getProduct();
		if (MLandedCost.LANDEDCOSTDISTRIBUTION_Line.equals(distribution))
			return Env.ONE;
		if (MLandedCost.LANDEDCOSTDISTRIBUTION_Volume.equals(distribution))
			return product == null || product.getVolume() == null ? Env.ZERO : qty.multiply(product.getVolume());
		if (MLandedCost.LANDEDCOSTDISTRIBUTION_Weight.equals(distribution))
			return product == null || product.getWeight() == null ? Env.ZERO : qty.multiply(product.getWeight());
		if (MLandedCost.LANDEDCOSTDISTRIBUTION_Costs.equals(distribution))
			return qty.multiply(orderLine.getPriceActual());
		return qty;
	}

	private static BigDecimal getSignedAmt(MInvoiceLine line) {
		BigDecimal amt = line.getLineNetAmt();
		if (line.getParent().isCreditMemo())
			amt = amt.negate();
		return amt;
	}

	private static BigDecimal getGeneratedAmt(MInvoiceLandedCost draft) {
		String sql = "SELECT COALESCE(SUM(Amt),0) FROM C_LandedCost WHERE C_InvoiceLandedCost_ID = ? "
				+ "AND Reversal_ID IS NULL AND DocStatus IN ('DR','IP','CO','CL')";
		BigDecimal result = org.compiere.util.DB.getSQLValueBD(draft.get_TrxName(), sql, draft.getC_InvoiceLandedCost_ID());
		return result == null ? Env.ZERO : result;
	}

	private static BigDecimal getLineGeneratedAmt(MInvoiceLine line) {
		String sql = "SELECT COALESCE(SUM(Amt),0) FROM C_LandedCost WHERE C_InvoiceLine_ID = ? "
				+ "AND Reversal_ID IS NULL AND DocStatus IN ('DR','IP','CO','CL')";
		BigDecimal result = org.compiere.util.DB.getSQLValueBD(line.get_TrxName(), sql, line.getC_InvoiceLine_ID());
		return result == null ? Env.ZERO : result;
	}

	private static boolean hasDocument(MInvoiceLandedCost draft, int receiptId) {
		return new Query(draft.getCtx(), MLandedCost.Table_Name,
				"C_InvoiceLandedCost_ID=? AND M_InOut_ID=? AND Reversal_ID IS NULL AND DocStatus IN ('DR','IP','CO','CL')",
				draft.get_TrxName())
				.setParameters(draft.getC_InvoiceLandedCost_ID(), receiptId)
				.match();
	}

	private static MLandedCost createDocument(MInvoiceLandedCost draft, MInvoiceLine line, MInOut receipt,
			BigDecimal amt, Timestamp dateOverride) {
		MInvoice invoice = line.getParent();
		MLandedCost doc = new MLandedCost(draft.getCtx(), 0, draft.get_TrxName());
		doc.setClientOrg(draft);
		doc.setC_Invoice_ID(invoice.getC_Invoice_ID());
		doc.setC_InvoiceLine_ID(line.getC_InvoiceLine_ID());
		doc.setC_InvoiceLandedCost_ID(draft.getC_InvoiceLandedCost_ID());
		doc.setC_Order_ID(draft.getScopeOrderId());
		doc.setM_InOut_ID(receipt != null ? receipt.getM_InOut_ID() : draft.getM_InOut_ID());
		if (draft.getM_InOutLine_ID() != 0)
			doc.setM_InOutLine_ID(draft.getM_InOutLine_ID());
		doc.setM_Product_ID(draft.getM_Product_ID());
		doc.setC_LandedCostType_ID(draft.getC_LandedCostType_ID());
		doc.setLandedCostDistribution(draft.getLandedCostDistribution());
		doc.setM_CostElement_ID(draft.getM_CostElement_ID());
		doc.setDescription(draft.getDescription());
		doc.setC_Currency_ID(invoice.getC_Currency_ID());
		doc.setC_ConversionType_ID(invoice.getC_ConversionType_ID());
		doc.setAmt(amt);
		Timestamp date = invoice.getDateAcct();
		if (receipt != null && receipt.getDateAcct() != null && receipt.getDateAcct().after(date))
			date = receipt.getDateAcct();
		if (dateOverride != null)
			date = dateOverride;
		doc.setDateDoc(date);
		doc.setDateAcct(date);
		doc.saveEx();
		if (!doc.processIt(DocAction.ACTION_Complete))
			throw new AdempiereException(doc.getProcessMsg());
		doc.saveEx();
		return doc;
	}

	public static List<MLandedCost> generateForLine(MInvoiceLine line, MInvoiceLandedCost onlyDraft, Timestamp dateOverride) {
		List<MLandedCost> created = new ArrayList<>();
		List<MInvoiceLandedCost> drafts = new ArrayList<>();
		for (MInvoiceLandedCost draft : getOfInvoiceLine(line.getCtx(), line.getC_InvoiceLine_ID(), line.get_TrxName())) {
			if (draft.isGenerated() || draft.isPreliminary())
				continue;
			if (onlyDraft != null && onlyDraft.getC_InvoiceLandedCost_ID() != draft.getC_InvoiceLandedCost_ID())
				continue;
			drafts.add(draft);
		}
		if (drafts.isEmpty())
			return created;
		int precision = MCurrency.getStdPrecision(line.getCtx(), line.getParent().getC_Currency_ID());
		LinkedHashSet<String> issues = new LinkedHashSet<>();
		List<BigDecimal> bases = new ArrayList<>();
		List<MInOut> receipts = new ArrayList<>();
		BigDecimal total = Env.ZERO;
		for (MInvoiceLandedCost draft : drafts) {
			List<MInOutLine> scope = draft.getScopeReceiptLines();
			if (scope.isEmpty()) {
				issues.add(Msg.getMsg(line.getCtx(), "LandedCostNoMatchError"));
				bases.add(Env.ZERO);
				receipts.add(null);
				continue;
			}
			MInOut receipt = scope.get(0).getParent();
			if (!MInOut.DOCSTATUS_Completed.equals(receipt.getDocStatus()) && !MInOut.DOCSTATUS_Closed.equals(receipt.getDocStatus()))
				issues.add(Msg.getMsg(line.getCtx(), "LandedCostReceiptNotCompleted", new Object[]{receipt.getDocumentNo()}));
			BigDecimal base = Env.ZERO;
			for (MInOutLine iol : scope) {
				BigDecimal lineBase = iol.getBase(draft.getLandedCostDistribution());
				String issue = MLandedCost.getBaseIssue(line.getCtx(), iol, draft.getLandedCostDistribution(), lineBase);
				if (issue != null)
					issues.add(issue);
				base = base.add(lineBase);
			}
			bases.add(base);
			receipts.add(receipt);
			total = total.add(base);
		}
		if (!issues.isEmpty())
			throw new AdempiereException(Msg.getMsg(line.getCtx(), "LandedCostDistributionError",
					new Object[]{String.join("\n", issues)}));
		if (total.signum() == 0)
			throw new AdempiereException(MLandedCost.getZeroBaseMessage(line.getCtx(), drafts.get(0).getLandedCostDistribution()));
		BigDecimal remaining = getSignedAmt(line).subtract(getLineGeneratedAmt(line));
		List<BigDecimal> amounts = LandedCostProration.prorate(remaining, bases, precision);
		for (int i = 0; i < drafts.size(); i++) {
			BigDecimal amt = amounts.get(i);
			MLandedCost doc = createDocument(drafts.get(i), line, receipts.get(i), amt, dateOverride);
			drafts.get(i).setIsGenerated(true);
			drafts.get(i).saveEx();
			created.add(doc);
		}
		return created;
	}

	public static MLandedCost generateForReceipt(MInvoiceLandedCost draft, MInOut receipt, Timestamp dateOverride) {
		if (!draft.isPreliminary() || hasDocument(draft, receipt.getM_InOut_ID()))
			return null;
		MInvoiceLine line = draft.getInvoiceLine();
		List<MOrderLine> orderLines = draft.getScopeOrderLines();
		if (orderLines.isEmpty())
			return null;
		String distribution = draft.getLandedCostDistribution();
		BigDecimal totalBase = Env.ZERO;
		boolean fullyDelivered = true;
		List<Integer> orderLineIds = new ArrayList<>();
		for (MOrderLine ol : orderLines) {
			orderLineIds.add(ol.getC_OrderLine_ID());
			totalBase = totalBase.add(getOrderBase(distribution, ol, ol.getQtyOrdered()));
			MOrderLine fresh = new MOrderLine(draft.getCtx(), ol.getC_OrderLine_ID(), draft.get_TrxName());
			if (fresh.getQtyDelivered().compareTo(fresh.getQtyOrdered()) < 0)
				fullyDelivered = false;
		}
		BigDecimal receiptBase = Env.ZERO;
		boolean touched = false;
		for (MInOutLine iol : receipt.getLines()) {
			int index = orderLineIds.indexOf(iol.getC_OrderLine_ID());
			if (iol.getC_OrderLine_ID() == 0 || index < 0)
				continue;
			touched = true;
			receiptBase = receiptBase.add(getOrderBase(distribution, orderLines.get(index), iol.getMovementQty()));
		}
		if (!touched)
			return null;
		if (receiptBase.signum() == 0 || totalBase.signum() == 0)
			throw new AdempiereException(MLandedCost.getZeroBaseMessage(draft.getCtx(), distribution));
		int precision = MCurrency.getStdPrecision(draft.getCtx(), line.getParent().getC_Currency_ID());
		BigDecimal lineAmt = getSignedAmt(line);
		BigDecimal remaining = lineAmt.subtract(getGeneratedAmt(draft));
		BigDecimal amt = fullyDelivered
				? remaining
				: lineAmt.multiply(receiptBase).divide(totalBase, MathContext.DECIMAL128).setScale(precision, RoundingMode.HALF_UP);
		if (remaining.signum() != 0 && amt.abs().compareTo(remaining.abs()) > 0)
			amt = remaining;
		MLandedCost doc = createDocument(draft, line, receipt, amt, dateOverride);
		if (fullyDelivered || amt.compareTo(remaining) == 0) {
			draft.setIsGenerated(true);
			draft.saveEx();
		}
		return doc;
	}

	public static List<MLandedCost> generateForReceipt(MInOut receipt) {
		List<MLandedCost> created = new ArrayList<>();
		java.util.Set<Integer> orderIds = new java.util.LinkedHashSet<>();
		for (MInOutLine iol : receipt.getLines()) {
			if (iol.getC_OrderLine_ID() != 0)
				orderIds.add(new MOrderLine(receipt.getCtx(), iol.getC_OrderLine_ID(), receipt.get_TrxName()).getC_Order_ID());
		}
		for (Integer orderId : orderIds) {
			for (MInvoiceLandedCost draft : getPendingOfOrder(receipt.getCtx(), orderId, receipt.get_TrxName())) {
				MLandedCost doc = generateForReceipt(draft, receipt, null);
				if (doc != null)
					created.add(doc);
			}
		}
		return created;
	}

	public static List<MLandedCost> generateForInvoice(MInvoice invoice) {
		List<MLandedCost> created = new ArrayList<>();
		for (MInvoiceLine line : invoice.getLines()) {
			created.addAll(generateForLine(line, null, null));
			for (MInvoiceLandedCost draft : getOfInvoiceLine(invoice.getCtx(), line.getC_InvoiceLine_ID(), invoice.get_TrxName())) {
				if (!draft.isPreliminary() || draft.isGenerated())
					continue;
				for (MInOut receipt : getCompletedReceipts(draft)) {
					MLandedCost doc = generateForReceipt(draft, receipt, null);
					if (doc != null)
						created.add(doc);
				}
			}
		}
		return created;
	}

	private static List<MInOut> getCompletedReceipts(MInvoiceLandedCost draft) {
		int orderId = draft.getScopeOrderId();
		String where = "IsSOTrx = 'N' AND DocStatus IN ('CO','CL') AND Reversal_ID IS NULL "
				+ "AND EXISTS(SELECT 1 FROM M_InOutLine iol INNER JOIN C_OrderLine ol ON (ol.C_OrderLine_ID = iol.C_OrderLine_ID) "
				+ "WHERE iol.M_InOut_ID = M_InOut.M_InOut_ID AND ol.C_Order_ID = ?)";
		return new Query(draft.getCtx(), MInOut.Table_Name, where, draft.get_TrxName())
				.setParameters(orderId)
				.setOrderBy("DateAcct, M_InOut_ID")
				.list();
	}
}
