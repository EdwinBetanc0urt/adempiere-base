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

package org.solop.project.process;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MProjectLine;
import org.compiere.process.DocAction;
import org.compiere.util.Util;
import org.solop.project.util.ProjectIssueGenerator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Issue to the project the selected project lines from purchase orders or from inventory.
 * <ul>
 * <li>Purchase order: a receipt is created and completed for each order with the selected quantities and
 * each receipt line is issued to the project line of the order line.</li>
 * <li>Inventory: the selected quantity is issued from the selected locator to the project line.</li>
 * </ul>
 */
public class ProjectIssueFromOrderInventory extends ProjectIssueFromOrderInventoryAbstract {

	/**	Source of the rows: purchase order	*/
	private static final String CREATE_FROM_ORDER = "O";
	/**	Source of the rows: inventory	*/
	private static final String CREATE_FROM_INVENTORY = "I";
	/**	Browse columns	*/
	private static final String COLUMN_CREATE_FROM_TYPE = "V_CreateFromType";
	private static final String COLUMN_PROJECT_LINE = "V_C_ProjectLine_ID";
	private static final String COLUMN_ORDER = "V_C_Order_ID";
	private static final String COLUMN_ORDER_LINE = "V_C_OrderLine_ID";
	private static final String COLUMN_LOCATOR = "V_M_Locator_ID";
	private static final String COLUMN_PRODUCT = "V_M_Product_ID";
	private static final String COLUMN_QTY_AVAILABLE = "V_QtyAvailable";
	private static final String COLUMN_MOVEMENT_QTY = "V_MovementQty";

	private ProjectIssueGenerator generator;
	private int receipts = 0;
	private int issues = 0;

	@Override
	protected String doIt() throws Exception {
		generator = ProjectIssueGenerator.newInstance(getCtx(), get_TrxName())
				.withMovementDate(getMovementDate())
				.withDescription(getDescription());
		List<Integer> keys = getSelectionKeys();
		keys.forEach(key -> ProjectIssueGenerator.validateQuantity(
				Optional.ofNullable(getSelectionAsBigDecimal(key, COLUMN_MOVEMENT_QTY)).orElse(BigDecimal.ZERO),
				Optional.ofNullable(getSelectionAsBigDecimal(key, COLUMN_QTY_AVAILABLE)).orElse(BigDecimal.ZERO)));
		Map<String, List<Integer>> keysByType = keys.stream()
				.collect(Collectors.groupingBy(key -> getCreateFromType(key), LinkedHashMap::new, Collectors.toList()));
		keysByType.getOrDefault(CREATE_FROM_ORDER, List.of()).stream()
				.collect(Collectors.groupingBy(key -> getSelectionAsInt(key, COLUMN_ORDER), LinkedHashMap::new, Collectors.toList()))
				.forEach(this::issueFromOrder);
		keysByType.getOrDefault(CREATE_FROM_INVENTORY, List.of())
				.forEach(this::issueFromInventory);
		return "@M_InOut_ID@: " + receipts + " - @C_ProjectIssue_ID@: " + issues;
	}

	/**
	 * Get the source of a selected row
	 */
	private String getCreateFromType(int key) {
		String createFromType = getSelectionAsString(key, COLUMN_CREATE_FROM_TYPE);
		if (!CREATE_FROM_ORDER.equals(createFromType) && !CREATE_FROM_INVENTORY.equals(createFromType)) {
			throw new AdempiereException("@CreateFromType@ @NotFound@");
		}
		return createFromType;
	}

	/**
	 * Receive the selected lines of a purchase order and issue the receipt lines to their project lines
	 * @param orderId purchase order
	 * @param keys selected rows of the order
	 */
	private void issueFromOrder(int orderId, List<Integer> keys) {
		MOrder order = new MOrder(getCtx(), orderId, get_TrxName());
		Timestamp movementDate = getMovementDate() != null ? getMovementDate() : new Timestamp(System.currentTimeMillis());
		MInOut receipt = new MInOut(order, 0, movementDate);
		if (!Util.isEmpty(getDescription(), true)) {
			receipt.setDescription(getDescription());
		}
		receipt.saveEx();
		keys.forEach(key -> {
			MOrderLine orderLine = new MOrderLine(getCtx(), getSelectionAsInt(key, COLUMN_ORDER_LINE), get_TrxName());
			MProjectLine projectLine = new MProjectLine(getCtx(), getSelectionAsInt(key, COLUMN_PROJECT_LINE), get_TrxName());
			BigDecimal quantity = Optional.ofNullable(getSelectionAsBigDecimal(key, COLUMN_MOVEMENT_QTY)).orElse(BigDecimal.ZERO);
			//	setOrderLine resolves the locator for storable products and leaves it empty for services, resources and charges
			MInOutLine receiptLine = new MInOutLine(receipt);
			receiptLine.setOrderLine(orderLine, getSelectionAsInt(key, COLUMN_LOCATOR), quantity);
			receiptLine.setQty(quantity);
			receiptLine.setC_Project_ID(projectLine.getC_Project_ID());
			receiptLine.setC_ProjectLine_ID(projectLine.getC_ProjectLine_ID());
			if (projectLine.getC_ProjectPhase_ID() > 0) {
				receiptLine.setC_ProjectPhase_ID(projectLine.getC_ProjectPhase_ID());
			}
			if (projectLine.getC_ProjectTask_ID() > 0) {
				receiptLine.setC_ProjectTask_ID(projectLine.getC_ProjectTask_ID());
			}
			receiptLine.saveEx();
		});
		if (!receipt.processIt(DocAction.ACTION_Complete)) {
			throw new AdempiereException("@M_InOut_ID@ " + receipt.getDocumentNo() + ": " + receipt.getProcessMsg());
		}
		receipt.saveEx();
		receipts++;
		addLog(receipt.getM_InOut_ID(), receipt.getMovementDate(), null, receipt.getDocumentNo());
		for (MInOutLine receiptLine : receipt.getLines(true)) {
			MProjectLine projectLine = new MProjectLine(getCtx(), receiptLine.getC_ProjectLine_ID(), get_TrxName());
			addIssueLog(generator.createIssue(projectLine, receiptLine.getM_Locator_ID(), receiptLine.getM_Product_ID(),
					receiptLine.getM_AttributeSetInstance_ID(), receiptLine.getMovementQty(), receiptLine.getDescription(),
					receiptLine.getM_InOutLine_ID(), 0, receiptLine.getC_Charge_ID()));
		}
	}

	/**
	 * Issue the selected quantity from the selected locator to the project line
	 * @param key selected row
	 */
	private void issueFromInventory(int key) {
		MProjectLine projectLine = new MProjectLine(getCtx(), getSelectionAsInt(key, COLUMN_PROJECT_LINE), get_TrxName());
		addIssueLog(generator.createIssue(projectLine, getSelectionAsInt(key, COLUMN_LOCATOR),
				getSelectionAsInt(key, COLUMN_PRODUCT), 0,
				Optional.ofNullable(getSelectionAsBigDecimal(key, COLUMN_MOVEMENT_QTY)).orElse(BigDecimal.ZERO),
				projectLine.getDescription()));
	}

	private void addIssueLog(MProjectIssue projectIssue) {
		issues++;
		addLog(projectIssue.getC_ProjectIssue_ID(), projectIssue.getMovementDate(), projectIssue.getMovementQty(),
				projectIssue.getDocumentNo());
	}
}
