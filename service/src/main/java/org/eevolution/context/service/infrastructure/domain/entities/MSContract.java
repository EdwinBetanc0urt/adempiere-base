/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.										*
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net or http://www.adempiere.net/license.html         *
 *****************************************************************************/
package org.eevolution.context.service.infrastructure.domain.entities;

import org.adempiere.core.domains.models.I_C_DocType;
import org.adempiere.core.domains.models.I_PP_Period;
import org.adempiere.core.domains.models.I_S_ContractLine;
import org.adempiere.core.domains.models.I_S_ContractTax;
import org.adempiere.core.domains.models.X_S_Contract;
import org.adempiere.core.domains.models.X_S_ContractTax;
import org.compiere.model.*;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.eevolution.manufacturing.model.MPPPeriod;
import org.eevolution.manufacturing.model.MPPPeriodDefinition;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Contract Entity
 */
public class MSContract extends X_S_Contract implements DocAction, DocOptions {

	public MSContract(Properties ctx, int S_Contract_ID, String trxName) {
		super(ctx, S_Contract_ID, trxName);
	}

	public MSContract(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	/** Process Message */
	private String processMessage = "";

	/** Just Prepared Flag */
	private boolean justPrepared = false;

	@Override
	protected boolean beforeSave(boolean newRecord) {
		//	Tax Included from Price List
		if (getM_PriceList_ID() > 0) {
			MPriceList priceList = MPriceList.get(getCtx(), getM_PriceList_ID(), get_TrxName());
			setIsTaxIncluded(priceList.isTaxIncluded());
		}
		return true;
	}

	/**
	 * Is Tax Included in Amount, based on Price List when it is defined
	 * @return true if tax is included
	 */
	@Override
	public boolean isTaxIncluded() {
		if (getM_PriceList_ID() > 0) {
			return MPriceList.get(getCtx(), getM_PriceList_ID(), get_TrxName()).isTaxIncluded();
		}
		return super.isTaxIncluded();
	}

	/**
	 * Get Currency Precision
	 * @return precision
	 */
	public int getPrecision() {
		return MCurrency.getStdPrecision(getCtx(), getC_Currency_ID());
	}

	/**
	 * Set Processed, also for contract taxes
	 * @param processed processed
	 */
	@Override
	public void setProcessed(boolean processed) {
		super.setProcessed(processed);
		if (get_ID() == 0) {
			return;
		}
		DB.executeUpdateEx(
			"UPDATE S_ContractTax SET Processed=? WHERE S_Contract_ID=?",
			new Object[]{processed, getS_Contract_ID()},
			get_TrxName()
		);
	}

	/**
	 * Calculate Tax and Total
	 * @return true if tax total calculated
	 */
	public boolean calculateTaxTotal() {
		log.fine("");
		//	Delete Taxes
		DB.executeUpdateEx(
			"DELETE S_ContractTax WHERE S_Contract_ID=?",
			new Object[]{getS_Contract_ID()},
			get_TrxName()
		);
		boolean isTaxIncluded = isTaxIncluded();
		int precision = getPrecision();
		//	Lines
		BigDecimal totalLines = Env.ZERO;
		List<Integer> taxList = new ArrayList<>();
		for (MSContractLine contractLine : getLines()) {
			totalLines = totalLines.add(Optional.ofNullable(contractLine.getLineNetAmt()).orElse(Env.ZERO));
			if (contractLine.getC_Tax_ID() <= 0 || taxList.contains(contractLine.getC_Tax_ID())) {
				continue;
			}
			X_S_ContractTax contractTax = createContractTax(contractLine.getC_Tax_ID(), isTaxIncluded);
			if (!calculateTaxFromLines(contractTax, isTaxIncluded, precision)) {
				return false;
			}
			contractTax.saveEx();
			taxList.add(contractLine.getC_Tax_ID());
		}
		//	Taxes
		BigDecimal grandTotal = totalLines;
		for (X_S_ContractTax contractTax : getTaxes()) {
			MTax tax = MTax.get(getCtx(), contractTax.getC_Tax_ID());
			if (tax.isSummary()) {
				for (MTax childTax : tax.getChildTaxes(false)) {
					BigDecimal taxAmount = childTax.calculateTax(contractTax.getTaxBaseAmt(), isTaxIncluded, precision);
					X_S_ContractTax childContractTax = createContractTax(childTax.getC_Tax_ID(), isTaxIncluded);
					childContractTax.setTaxBaseAmt(contractTax.getTaxBaseAmt());
					childContractTax.setTaxAmt(taxAmount);
					childContractTax.saveEx();
					if (!isTaxIncluded)
						grandTotal = grandTotal.add(taxAmount);
				}
				contractTax.deleteEx(true);
			} else if (!isTaxIncluded) {
				grandTotal = grandTotal.add(contractTax.getTaxAmt());
			}
		}
		setTotalLines(totalLines);
		setGrandTotal(grandTotal);
		return true;
	}

	/**
	 * Create a new contract tax (not saved)
	 * @param taxId tax
	 * @param isTaxIncluded tax included
	 * @return contract tax
	 */
	private X_S_ContractTax createContractTax(int taxId, boolean isTaxIncluded) {
		X_S_ContractTax contractTax = new X_S_ContractTax(getCtx(), 0, get_TrxName());
		contractTax.setAD_Org_ID(getAD_Org_ID());
		contractTax.setS_Contract_ID(getS_Contract_ID());
		contractTax.setC_Tax_ID(taxId);
		contractTax.setIsTaxIncluded(isTaxIncluded);
		contractTax.setTaxBaseAmt(Env.ZERO);
		contractTax.setTaxAmt(Env.ZERO);
		return contractTax;
	}

	/**
	 * Calculate/Set Tax Amt from Contract Lines
	 * @param contractTax contract tax
	 * @param isTaxIncluded tax included
	 * @param precision precision
	 * @return true if calculated
	 */
	private boolean calculateTaxFromLines(X_S_ContractTax contractTax, boolean isTaxIncluded, int precision) {
		MTax tax = MTax.get(getCtx(), contractTax.getC_Tax_ID());
		boolean documentLevel = tax.isDocumentLevel();
		BigDecimal taxBaseAmt = Env.ZERO;
		BigDecimal taxAmt = Env.ZERO;
		List<BigDecimal> lineNetAmounts = new Query(
			getCtx(),
			I_S_ContractLine.Table_Name,
			I_S_ContractLine.COLUMNNAME_S_Contract_ID + "=? AND " + I_S_ContractLine.COLUMNNAME_C_Tax_ID + "=?",
			get_TrxName()
		)
			.setParameters(getS_Contract_ID(), contractTax.getC_Tax_ID())
			.<MSContractLine>list()
			.stream()
			.map(contractLine -> Optional.ofNullable(contractLine.getLineNetAmt()).orElse(Env.ZERO))
			.collect(Collectors.toList())
		;
		for (BigDecimal baseAmt : lineNetAmounts) {
			taxBaseAmt = taxBaseAmt.add(baseAmt);
			if (!documentLevel) {
				// calculate line tax
				taxAmt = taxAmt.add(tax.calculateTax(baseAmt, isTaxIncluded, precision));
			}
		}
		//	Calculate Tax
		if (documentLevel) {
			// calculate header tax
			taxAmt = tax.calculateTax(taxBaseAmt, isTaxIncluded, precision);
		}
		contractTax.setTaxAmt(taxAmt);
		//	Set Base
		if (isTaxIncluded) {
			contractTax.setTaxBaseAmt(taxBaseAmt.subtract(taxAmt));
		}
		else {
			contractTax.setTaxBaseAmt(taxBaseAmt);
		}
		return true;
	}

	/**
	 * Get Contract Taxes
	 * @return taxes
	 */
	public List<X_S_ContractTax> getTaxes() {
		return new Query(
			getCtx(),
			I_S_ContractTax.Table_Name,
			I_S_ContractTax.COLUMNNAME_S_Contract_ID + "=? ",
			get_TrxName()
		)
			.setParameters(getS_Contract_ID())
			.list()
		;
	}

	@Override
	protected boolean afterSave(boolean newRecord, boolean success) {
		return true;
	}

	@Override
	protected boolean beforeDelete() {
		return true;
	}

	@Override
	public String getDocumentInfo() {
		MDocType documentType = MDocType.get(getCtx(), getC_DocType_ID());
		return documentType.getName() + "   " + getDocumentNo();
	}

	@Override
	public boolean processIt(String processAction) throws Exception {
		DocumentEngine engine = new DocumentEngine(this, getDocStatus());
		return engine.processIt(processAction, getDocAction());
	}

	@Override
	public boolean unlockIt() {
		log.info("unlockIt -  " + toString());
		return true;
	}

	@Override
	public boolean invalidateIt() {
		log.info("invalidateIt - " + toString());
		setDocAction(X_S_Contract.DOCACTION_Prepare);
		return true;
	}

	@Override
	public String prepareIt() {
		log.info(toString());
		processMessage = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (processMessage != null)
			return DocAction.STATUS_Invalid;

		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());

		//	Std Period open?
		if (!MPeriod.isOpen(getCtx(), getDateDoc(), dt.getDocBaseType(), getAD_Org_ID())) {
			processMessage = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}

		List<MSContractLine> contractLines = getLines();
		if (contractLines.isEmpty()) {
			processMessage = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}

		//	Calculate Taxes and Totals
		if (!calculateTaxTotal()) {
			processMessage = "Error calculating tax";
			return DocAction.STATUS_Invalid;
		}

		//	Planned summarization from the contract's own lines
		updatePlannedFromLines();

		//	Add up Amounts
		processMessage = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (processMessage != null)
			return DocAction.STATUS_Invalid;
		justPrepared = true;
		if (!X_S_Contract.DOCACTION_Complete.equals(getDocAction()))
			setDocAction(X_S_Contract.DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}

	@Override
	public boolean approveIt() {
		log.info("approveIt - " + toString());
		setIsApproved(true);
		return true;
	}

	@Override
	public boolean rejectIt() {
		log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	}

	@Override
	public String completeIt() {
		//	Re-Check
		if (!justPrepared) {
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		processMessage = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (processMessage != null) {
			return DocAction.STATUS_Invalid;
		}

		//	Implicit Approval
		if (!isApproved())
			approveIt();
		log.info(toString());
		//
		if (getPP_PeriodDefinition_ID() > 0)
			creteTimeSheetReportExpense();

		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null) {
			processMessage = valid;
			return DocAction.STATUS_Invalid;
		}
		//	Set Definitive Document No
		setDefiniteDocumentNo();
		setProcessed(true);
		setDocAction(X_S_Contract.DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}

	public void creteTimeSheetReportExpense() {
		int lineNo = 10;
		MPPPeriodDefinition periodDefinition = new MPPPeriodDefinition(getCtx(), getPP_PeriodDefinition_ID(), get_TrxName());
		for (MSContractLine contractLine : getLines()) {
			for (MPPPeriod period : periodDefinition.getPeriodsOrderBy(I_PP_Period.COLUMNNAME_PeriodNo)) {
				MTimeExpense timeReport = new MTimeExpense(getCtx(), 0, get_TrxName());
				timeReport.set_ValueOfColumn("S_Contract_ID", get_ID());
				timeReport.setC_BPartner_ID(getC_BPartner_ID());
				timeReport.setDateReport(period.getStartDate());
				timeReport.set_ValueOfColumn("PP_PeriodDefinition_ID", periodDefinition.get_ID());
				timeReport.set_ValueOfColumn("PP_Period_ID", period.get_ID());
				timeReport.set_ValueOfColumn("PP_Calendar_ID", periodDefinition.getPP_Calendar_ID());
				timeReport.setDescription(contractLine.getDescription());
				timeReport.setM_PriceList_ID(getM_PriceList_ID());
				timeReport.setM_Warehouse_ID(getM_Warehouse_ID());
				timeReport.set_ValueOfColumn("C_Project_ID", getC_Project_ID());
				timeReport.set_ValueOfColumn("C_Activity_ID", getC_Activity_ID());
				timeReport.set_ValueOfColumn("C_Campaign_ID", getC_Campaign_ID());
				timeReport.setAD_Org_ID(getAD_Org_ID());
				timeReport.set_ValueOfColumn("AD_OrgTrx_ID", getAD_OrgTrx_ID());
				timeReport.set_ValueOfColumn("User1_ID", getUser1_ID());
				timeReport.set_ValueOfColumn("User2_ID", getUser1_ID());
				timeReport.set_ValueOfColumn("User3_ID", getUser1_ID());
				timeReport.set_ValueOfColumn("User4_ID", getUser1_ID());
				timeReport.setDocStatus(DocAction.ACTION_Complete);
				timeReport.setDocAction(DocAction.STATUS_Drafted);
				timeReport.set_ValueOfColumn("IsSOTrx", isSOTrx());
				timeReport.saveEx();

				MTimeExpenseLine timeReportLine = new MTimeExpenseLine(getCtx(), 0, get_TableName());
				timeReportLine.setS_TimeExpense_ID(timeReport.get_ID());
				timeReportLine.set_ValueOfColumn("S_ContractLine_ID", contractLine.get_ID());
				timeReportLine.setLine(lineNo);
				timeReportLine.set_ValueOfColumn("PP_Calendar_ID", periodDefinition.getPP_Calendar_ID());
				timeReportLine.set_ValueOfColumn("PP_PeriodDefinition_ID", periodDefinition.get_ID());
				timeReportLine.set_ValueOfColumn("PP_Period_ID", period.get_ID());
				timeReportLine.setDateExpense(period.getStartDate());
				timeReportLine.setM_Product_ID(contractLine.getM_Product_ID());
				timeReportLine.setQty(contractLine.getQtyOrdered());
				timeReportLine.setC_BPartner_ID(contractLine.getC_BPartner_ID());
				timeReportLine.setDescription(contractLine.getDescription());
				if (isSOTrx()) {
					timeReportLine.setIsInvoiced(true);
					timeReportLine.setInvoicePrice(contractLine.getPriceActual());
				} else {
					timeReportLine.setExpenseAmt(contractLine.getPriceActual());
				}

				timeReportLine.setC_Currency_ID(contractLine.getC_Currency_ID());
				timeReportLine.setC_Tax_ID(contractLine.getC_Tax_ID());
				timeReportLine.setLineNetAmt(contractLine.getLineNetAmt());
				timeReportLine.setC_Activity_ID(contractLine.getC_Activity_ID());
				timeReportLine.setC_Campaign_ID(contractLine.getC_Campaign_ID());
				timeReportLine.setAD_Org_ID(contractLine.getAD_Org_ID());
				timeReportLine.set_ValueOfColumn("AD_OrgTrx_ID", contractLine.getAD_OrgTrx_ID());
				timeReportLine.set_ValueOfColumn("User1_ID", contractLine.getUser1_ID());
				timeReportLine.set_ValueOfColumn("User2_ID", contractLine.getUser1_ID());
				timeReportLine.set_ValueOfColumn("User3_ID", contractLine.getUser1_ID());
				timeReportLine.set_ValueOfColumn("User4_ID", contractLine.getUser1_ID());
				timeReportLine.set_ValueOfColumn("C_Project_ID", contractLine.getC_Project_ID());
				timeReportLine.setC_ProjectPhase_ID(contractLine.getC_ProjectPhase_ID());
				timeReportLine.setC_ProjectTask_ID(contractLine.getC_ProjectTask_ID());
				timeReportLine.saveEx();
				lineNo = lineNo + 10;
			}
		}
	}

	private void setDefiniteDocumentNo() {
		MDocType documentType = MDocType.get(getCtx(), getC_DocType_ID());
		if (documentType.isOverwriteDateOnComplete())
			setDateDoc(new Timestamp(System.currentTimeMillis()));
		if (documentType.isOverwriteSeqOnComplete()) {
			String value = "";
			int index = p_info.getColumnIndex(I_C_DocType.COLUMNNAME_C_DocType_ID);
			if (index != -1)
				value = DB.getDocumentNo(get_ValueAsInt(index), get_TrxName(), true);
			if (value != null)
				setDocumentNo(value);
		}
	}

	@Override
	public boolean voidIt() {
		log.info("voidIt - " + toString());
		return closeIt();
	}

	@Override
	public boolean closeIt() {
		log.info("closeIt -  " + toString());
		setDocAction(X_S_Contract.DOCACTION_None);
		return true;
	}

	@Override
	public boolean reverseCorrectIt() {
		log.info("reverseCorrectIt - " + toString());
		return false;
	}

	@Override
	public boolean reverseAccrualIt() {
		log.info("reverseAccrualIt - " + toString());
		return false;
	}

	@Override
	public boolean reActivateIt() {
		log.info("reActivateIt - " + toString());
		// Before reActivate
		processMessage = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (processMessage != null)
			return false;
		MDocType documentType = MDocType.get(getCtx(), getC_DocType_ID());

		// After reActivate
		processMessage = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_REACTIVATE);
		if (processMessage != null) {
			return false;
		}

		setDocAction(DocAction.ACTION_Complete);
		setProcessed(false);
		setPosted(false);
		return true;
	}

	@Override
	public int customizeValidActions(String docStatus, Object processing, String orderType, String isSOTrx,
			int AD_Table_ID, String[] docAction, String[] options, int index) {
		//	Valid Document Action
		int seqIndex = index;
		if (AD_Table_ID == Table_ID) {
			if (docStatus.equals(DocAction.STATUS_Drafted)
					|| docStatus.equals(DocAction.STATUS_InProgress)
					|| docStatus.equals(DocAction.STATUS_Invalid)) {
				options[seqIndex++] = DocAction.ACTION_Prepare;
			} else { //	Complete                    ..  CO
				if (docStatus.equals(DocAction.STATUS_Completed)) {
					options[seqIndex++] = DocAction.ACTION_Void;
					options[seqIndex++] = DocAction.ACTION_ReActivate;
					options[seqIndex++] = DocAction.ACTION_Close;
				} else if (docStatus.equals(DocAction.STATUS_Closed)) {
					options[seqIndex++] = DocAction.ACTION_None;
				}
			}
		}
		return seqIndex;
	}

	@Override
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		sb.append(getDocumentNo());
		if (getDescription() != null && getDescription().length() > 0)
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}

	public File createPDF(File file) {
		// ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
		// if (re == null)
		return null;
		//	createPDF//	return re.getPDF(file);
	}

	@Override
	public File createPDF() {
		try {
			File temp = File.createTempFile(get_TableName() + get_ID() + "_", ".pdf");
			return createPDF(temp);
		} catch (Exception e) {
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}

	@Override
	public String getProcessMsg() {
		return processMessage;
	}

	@Override
	public int getDoc_User_ID() {
		return getCreatedBy();
	}

	@Override
	public BigDecimal getApprovalAmt() {
		return BigDecimal.ZERO;
	}

	public List<MSContractLine> getLines() {
		String whereClause = I_S_ContractLine.COLUMNNAME_S_Contract_ID + "=?";
		return new Query(getCtx(), I_S_ContractLine.Table_Name, whereClause, get_TrxName())
				.setParameters(get_ID())
				.list();
	}

	/**
	 * Recompute the header planned summarization columns from the contract's own lines.
	 * These are planning values (independent of documents): PlannedAmt/PlannedQty from the
	 * lines, CostPlannedAmt from qty*cost, and the derived margin/profit/balance.
	 */
	public void updatePlannedFromLines() {
		BigDecimal plannedAmt = Env.ZERO;
		BigDecimal plannedQty = Env.ZERO;
		BigDecimal costPlannedAmt = Env.ZERO;
		for (MSContractLine line : getLines()) {
			if (!line.isActive())
				continue;
			BigDecimal qty = Optional.ofNullable(line.getQtyEntered()).orElse(Env.ZERO);
			BigDecimal cost = Optional.ofNullable(line.getPriceCost()).orElse(Env.ZERO);
			plannedAmt = plannedAmt.add(Optional.ofNullable(line.getLineNetAmt()).orElse(Env.ZERO));
			plannedQty = plannedQty.add(qty);
			costPlannedAmt = costPlannedAmt.add(qty.multiply(cost));
		}
		BigDecimal plannedMarginAmt = plannedAmt.subtract(costPlannedAmt);
		setPlannedAmt(plannedAmt);
		setPlannedQty(plannedQty);
		setCostPlannedAmt(costPlannedAmt);
		setPlannedMarginAmt(plannedMarginAmt);
		setProfitPlannedAmt(plannedMarginAmt);
		setProjectBalanceAmt(plannedAmt.subtract(getInvoicedAmt()));
	}

	/**
	 * Recalculate the document-driven summarization columns of the given contracts and save them.
	 * Called from document completion (MOrder/MInvoice/MInOut) whose header references a contract.
	 * The map carries the amount/qty the completing document contributes (its own lines), added on
	 * top of the SQL aggregation because the document is not yet CO/CL in the DB at this point.
	 */
	public static void recalculateContracts(Properties ctx, Map<Integer, BigDecimal[]> contractAmtQty,
		String trxName, String tableName, boolean isSOTrx) {
		if (contractAmtQty == null || contractAmtQty.isEmpty())
			return;
		for (Map.Entry<Integer, BigDecimal[]> entry : contractAmtQty.entrySet()) {
			Integer contractId = entry.getKey();
			if (contractId == null || contractId <= 0)
				continue;
			MSContract contract = new MSContract(ctx, contractId, trxName);
			if (contract.get_ID() != contractId)
				continue;
			contract.recalculateFromDocument(tableName, isSOTrx, entry.getValue());
			contract.saveEx();
		}
	}

	private void recalculateFromDocument(String tableName, boolean isSOTrx, BigDecimal[] newAmtQty) {
		if (MOrder.Table_Name.equals(tableName)) {
			if (isSOTrx) {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(ol.LineNetAmt),0), COALESCE(SUM(ol.QtyOrdered),0) "
					+ "FROM " + MOrderLine.Table_Name + " ol JOIN " + MOrder.Table_Name + " o ON o.C_Order_ID=ol.C_Order_ID "
					+ "WHERE o.S_Contract_ID=? AND o.IsSOTrx='Y' AND o.DocStatus IN ('CO','CL')", newAmtQty);
				setOrderedAmt(amtQty[0]);
				setQtyOrdered(amtQty[1]);
				setCommittedAmt(amtQty[0]);
				setCommittedQty(amtQty[1]);
			} else {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(ol.LineNetAmt),0), COALESCE(SUM(ol.QtyOrdered),0) "
					+ "FROM " + MOrderLine.Table_Name + " ol JOIN " + MOrder.Table_Name + " o ON o.C_Order_ID=ol.C_Order_ID "
					+ "WHERE o.S_Contract_ID=? AND o.IsSOTrx='N' AND o.DocStatus IN ('CO','CL')", newAmtQty);
				setCostOrderedAmt(amtQty[0]);
				setCostOrderedQty(amtQty[1]);
			}
		} else if (MInvoice.Table_Name.equals(tableName)) {
			if (isSOTrx) {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(il.LineNetAmt),0), COALESCE(SUM(il.QtyInvoiced),0) "
					+ "FROM " + MInvoiceLine.Table_Name + " il JOIN " + MInvoice.Table_Name + " i ON i.C_Invoice_ID=il.C_Invoice_ID "
					+ "WHERE i.S_Contract_ID=? AND i.IsSOTrx='Y' AND i.DocStatus IN ('CO','CL')", newAmtQty);
				setInvoicedAmt(amtQty[0]);
				setInvoicedQty(amtQty[1]);
			} else {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(il.LineNetAmt),0), COALESCE(SUM(il.QtyInvoiced),0) "
					+ "FROM " + MInvoiceLine.Table_Name + " il JOIN " + MInvoice.Table_Name + " i ON i.C_Invoice_ID=il.C_Invoice_ID "
					+ "WHERE i.S_Contract_ID=? AND i.IsSOTrx='N' AND i.DocStatus IN ('CO','CL')", newAmtQty);
				setCostInvoicedAmt(amtQty[0]);
				setCostInvoicedQty(amtQty[1]);
			}
			setProfitRealizedAmt(getInvoicedAmt().subtract(getCostInvoicedAmt()));
		} else if (MInOut.Table_Name.equals(tableName)) {
			if (isSOTrx) {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(iol.MovementQty*COALESCE(ol.PriceActual,0)),0), COALESCE(SUM(iol.MovementQty),0) "
					+ "FROM " + MInOutLine.Table_Name + " iol JOIN " + MInOut.Table_Name + " io ON io.M_InOut_ID=iol.M_InOut_ID "
					+ "LEFT JOIN " + MOrderLine.Table_Name + " ol ON ol.C_OrderLine_ID=iol.C_OrderLine_ID "
					+ "WHERE io.S_Contract_ID=? AND io.IsSOTrx='Y' AND io.DocStatus IN ('CO','CL')", newAmtQty);
				setDeliveredAmt(amtQty[0]);
				setQtyDelivered(amtQty[1]);
			} else {
				BigDecimal[] amtQty = getDocumentAmtQty(
					"SELECT COALESCE(SUM(iol.MovementQty*COALESCE(ol.PriceActual,0)),0), COALESCE(SUM(iol.MovementQty),0) "
					+ "FROM " + MInOutLine.Table_Name + " iol JOIN " + MInOut.Table_Name + " io ON io.M_InOut_ID=iol.M_InOut_ID "
					+ "LEFT JOIN " + MOrderLine.Table_Name + " ol ON ol.C_OrderLine_ID=iol.C_OrderLine_ID "
					+ "WHERE io.S_Contract_ID=? AND io.IsSOTrx='N' AND io.DocStatus IN ('CO','CL')", newAmtQty);
				setCostReceivedAmt(amtQty[0]);
				setCostReceivedQty(amtQty[1]);
			}
		}
	}

	private BigDecimal[] getDocumentAmtQty(String sql, BigDecimal[] newAmtQty) {
		BigDecimal[] result = new BigDecimal[] { Env.ZERO, Env.ZERO };
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, get_TrxName());
			pstmt.setInt(1, get_ID());
			rs = pstmt.executeQuery();
			if (rs.next()) {
				result[0] = Optional.ofNullable(rs.getBigDecimal(1)).orElse(Env.ZERO);
				result[1] = Optional.ofNullable(rs.getBigDecimal(2)).orElse(Env.ZERO);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, sql, e);
		} finally {
			DB.close(rs, pstmt);
		}
		if (newAmtQty != null) {
			result[0] = result[0].add(newAmtQty[0]);
			result[1] = result[1].add(newAmtQty[1]);
		}
		return result;
	}

}	//	MSContract
