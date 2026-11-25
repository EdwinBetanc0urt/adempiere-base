/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.									  *
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

package org.spin.tar.process;

import org.adempiere.core.domains.models.X_HR_Incidence;
import org.adempiere.core.domains.models.X_S_Contract;
import org.adempiere.core.domains.models.X_S_ContractLine;
import org.adempiere.core.domains.models.X_S_TimeExpense;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MProductPricing;
import org.compiere.model.MTimeExpense;
import org.compiere.model.MTimeExpenseLine;
import org.compiere.util.Util;
import org.spin.tar.model.MHRIncidence;
import org.spin.tar.model.MHRShiftIncidence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Generated Process for (Create Expense Report (From Incidence))
 *  @author ADempiere (generated)
 *  @version Release 3.9.0
 */
public class CreateExpenseReportFromIncidence extends CreateExpenseReportFromIncidenceAbstract {

	@Override
	protected String doIt() throws Exception {
		boolean preserveIncidenceDetail = !"N".equals(getIsPreserveIncidenceDetail());
		int created = 0;
		Map<String, MTimeExpense> expenseReportByKey = new LinkedHashMap<>();
		Map<String, Map<String, MTimeExpenseLine>> linesByReportKey = new LinkedHashMap<>();
		//	Loop for keys
		for(Integer key : getSelectionKeys()) {
			MHRIncidence incidence = new MHRIncidence(getCtx(), key, get_TrxName());
			if(!incidence.getDocStatus().equals(X_HR_Incidence.DOCSTATUS_Completed)) {
				continue;
			}
			MHRShiftIncidence shiftIncidence = new MHRShiftIncidence(getCtx(), incidence.getHR_ShiftIncidence_ID(), get_TrxName());
			int bPartnerId = incidence.getC_BPartner_ID();
			int contractId = incidence.getS_Contract_ID();
			int contractLineId = incidence.getS_ContractLine_ID();
			int projectId = incidence.getC_Project_ID();
			//	Resolve / create the Expense Report header for this (BPartner, Contract, Project)
			String reportKey = bPartnerId + "#" + contractId + "#" + projectId;
			MTimeExpense expenseReport = expenseReportByKey.get(reportKey);
			if(expenseReport == null) {
				expenseReport = createExpenseReport(bPartnerId, contractId, projectId);
				expenseReportByKey.put(reportKey, expenseReport);
				linesByReportKey.put(reportKey, new LinkedHashMap<>());
			}
			//	Quantity
			BigDecimal qty = getSelectionAsBigDecimal(key, "HI_Qty");
			//	Validate product
			if(expenseReport.getM_PriceList_ID() <= 0) {
				throw new AdempiereException("@M_PriceList_ID@ @NotFound@");
			}
			//	If the incidence has a contract line, use its product and price
			X_S_ContractLine contractLine = contractLineId > 0
					? new X_S_ContractLine(getCtx(), contractLineId, get_TrxName())
					: null;
			int productId = 0;
			BigDecimal expenseAmt = BigDecimal.ZERO;
			if(contractLine != null
					&& contractLine.getM_Product_ID() != 0) {
				productId = contractLine.getM_Product_ID();
				expenseAmt = contractLine.getPriceActual();
			} else if(shiftIncidence.getM_Product_ID() != 0) {
				productId = shiftIncidence.getM_Product_ID();
				//	Get price from price list
				MProductPricing productPricing = new MProductPricing(productId,
						expenseReport.getC_BPartner_ID(), qty, expenseReport.isSOTrx(), get_TrxName());
				productPricing.setM_PriceList_ID(expenseReport.getM_PriceList_ID());
				expenseAmt = productPricing.getPriceStd();
			}
			if(expenseAmt == null) {
				expenseAmt = BigDecimal.ZERO;
			}
			boolean isTimeReport = !shiftIncidence.isInvoiced();
			boolean isInvoiced = (Util.isEmpty(getIsInvoiced())
					? shiftIncidence.isInvoiced()
							: getIsInvoiced().equals("Y"));
			//	Consolidate: reuse a line with the same product / contract line / project / price / flags
			MTimeExpenseLine expenseLine = null;
			String lineKey = productId + "#" + contractLineId + "#" + projectId + "#"
					+ expenseAmt.stripTrailingZeros().toPlainString() + "#"
					+ isInvoiced + "#" + isTimeReport;
			if(!preserveIncidenceDetail) {
				expenseLine = linesByReportKey.get(reportKey).get(lineKey);
			}
			if(expenseLine != null) {
				//	Accumulate quantity on the existing line
				expenseLine.setQty(expenseLine.getQty().add(qty));
				expenseLine.saveEx();
			} else {
				//	Create new line
				expenseLine = new MTimeExpenseLine(getCtx(), 0, get_TrxName());
				expenseLine.setS_TimeExpense_ID(expenseReport.getS_TimeExpense_ID());
				expenseLine.setDateExpense(getDateReport());
				expenseLine.setIsTimeReport(isTimeReport);
				expenseLine.setIsInvoiced(isInvoiced);
				if(productId != 0) {
					expenseLine.setM_Product_ID(productId);
					expenseLine.setExpenseAmt(expenseAmt);
				}
				expenseLine.setQty(qty);
				expenseLine.setC_BPartner_ID(expenseReport.getC_BPartner_ID());
				if(projectId > 0) {
					expenseLine.setC_Project_ID(projectId);
				}
				if(contractLineId > 0) {
					expenseLine.set_ValueOfColumn("S_ContractLine_ID", contractLineId);
				}
				expenseLine.saveEx();
				if(!preserveIncidenceDetail) {
					linesByReportKey.get(reportKey).put(lineKey, expenseLine);
				}
			}
			//	Stamp the incidence with the generated header / line
			incidence.setS_TimeExpense_ID(expenseReport.getS_TimeExpense_ID());
			incidence.setS_TimeExpenseLine_ID(expenseLine.getS_TimeExpenseLine_ID());
			incidence.saveEx();
			created++;
		}
		//	Complete every generated Expense Report
		for(MTimeExpense expenseReport : expenseReportByKey.values()) {
			processDocument(expenseReport);
		}
		return "@Created@ " + created;
	}

	/**
	 * Process Expense
	 */
	private void processDocument(MTimeExpense expenseReport) {
		if(expenseReport == null) {
			return;
		}
		//	Process Selection
		if(!expenseReport.processIt(getDocAction())) {
			throw new AdempiereException("@Error@ " + expenseReport.getProcessMsg());
		}
		//
		expenseReport.saveEx();
		addLog(expenseReport.getS_TimeExpense_ID(), null, null, expenseReport.toString());
	}

	/**
	 * Create Expense Report
	 * @param bPartnerId Business Partner from the incidence
	 * @param contractId Contract from the incidence
	 * @param projectId Project from the incidence
	 * @return the saved Expense Report header
	 */
	private MTimeExpense createExpenseReport(int bPartnerId, int contractId, int projectId) {
		MTimeExpense expenseReport = new MTimeExpense(getCtx(), 0, get_TrxName());
		expenseReport.setDocStatus(X_S_TimeExpense.DOCSTATUS_Drafted);
		expenseReport.setDocAction(X_S_TimeExpense.DOCACTION_Complete);
		expenseReport.setC_BPartner_ID(bPartnerId);
		expenseReport.setDateReport(getDateReport());
		expenseReport.setM_Warehouse_ID(getWarehouseId());
		expenseReport.setM_PriceList_ID(getPriceListId());
		if(projectId > 0) {
			expenseReport.setC_Project_ID(projectId);
		}
		if(contractId > 0) {
			X_S_Contract contract = new X_S_Contract(getCtx(), contractId, get_TrxName());
			expenseReport.setC_BPartner_ID(contract.getC_BPartner_ID());
			expenseReport.set_ValueOfColumn("Bill_BPartner_ID", contract.getBill_BPartner_ID());
			expenseReport.set_ValueOfColumn("S_Contract_ID", contractId);
		}
		expenseReport.saveEx();
		return expenseReport;
	}

}
