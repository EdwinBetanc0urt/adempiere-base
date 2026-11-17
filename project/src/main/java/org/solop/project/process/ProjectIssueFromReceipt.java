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
import org.compiere.model.MInOutLine;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProject;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MProjectLine;
import org.compiere.util.Util;
import org.solop.project.util.ProjectIssueGenerator;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Issue to the project the selected lines of completed vendor receipts.
 * The selection key is the receipt line and the issued quantity is taken from the browse.
 */
public class ProjectIssueFromReceipt extends ProjectIssueFromReceiptAbstract {

	/**	Browse column of the quantity to issue	*/
	private static final String COLUMN_MOVEMENT_QTY = "IOL_MovementQty";
	/**	Browse column of the quantity pending to issue	*/
	private static final String COLUMN_QTY_PENDING = "IOL_QtyPending";

	@Override
	protected String doIt() throws Exception {
		ProjectIssueGenerator generator = ProjectIssueGenerator.newInstance(getCtx(), get_TrxName())
				.withMovementDate(getMovementDate())
				.withDescription(getDescription());
		int created = 0;
		for (int inOutLineId : getSelectionKeys()) {
			BigDecimal quantity = Optional.ofNullable(getSelectionAsBigDecimal(inOutLineId, COLUMN_MOVEMENT_QTY))
					.orElse(BigDecimal.ZERO);
			ProjectIssueGenerator.validateQuantity(quantity,
					Optional.ofNullable(getSelectionAsBigDecimal(inOutLineId, COLUMN_QTY_PENDING)).orElse(BigDecimal.ZERO));
			MInOutLine inOutLine = new MInOutLine(getCtx(), inOutLineId, get_TrxName());
			if (inOutLine.getC_Project_ID() <= 0) {
				throw new AdempiereException("@C_Project_ID@ @NotFound@ - @M_InOutLine_ID@ " + inOutLine.getLine());
			}
			MProject project = new MProject(getCtx(), inOutLine.getC_Project_ID(), get_TrxName());
			MProjectLine projectLine = generator.getProjectLine(project, getProjectLineId(inOutLine),
					inOutLine.getM_Product_ID(), inOutLine.getC_ProjectPhase_ID(), inOutLine.getC_ProjectTask_ID());
			String sourceDescription = Util.isEmpty(inOutLine.getDescription(), true)
					? inOutLine.getParent().getDescription()
					: inOutLine.getDescription();
			MProjectIssue projectIssue = generator.createIssue(projectLine, inOutLine.getM_Locator_ID(),
					inOutLine.getM_Product_ID(), inOutLine.getM_AttributeSetInstance_ID(), quantity, sourceDescription,
					inOutLine.getM_InOutLine_ID(), 0, inOutLine.getC_Charge_ID());
			addLog(projectIssue.getC_ProjectIssue_ID(), projectIssue.getMovementDate(), projectIssue.getMovementQty(),
					projectIssue.getDocumentNo());
			created++;
		}
		return "@Created@ " + created;
	}

	/**
	 * Get the project line of the receipt line or of its purchase order line
	 */
	private int getProjectLineId(MInOutLine inOutLine) {
		int projectLineId = inOutLine.get_ValueAsInt(MProjectLine.COLUMNNAME_C_ProjectLine_ID);
		if (projectLineId <= 0 && inOutLine.getC_OrderLine_ID() > 0) {
			projectLineId = new MOrderLine(getCtx(), inOutLine.getC_OrderLine_ID(), get_TrxName()).getC_ProjectLine_ID();
		}
		return projectLineId;
	}
}
