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
import org.compiere.model.MProject;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MProjectLine;
import org.compiere.model.MStorage;
import org.compiere.model.MTimeExpense;
import org.compiere.model.MTimeExpenseLine;
import org.compiere.util.Util;
import org.solop.project.util.ProjectIssueGenerator;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Issue to the project the selected lines of completed expense reports.
 * The selection key is the expense report line and the issued quantity is taken from the browse.
 */
public class ProjectIssueFromTimeExpense extends ProjectIssueFromTimeExpenseAbstract {

	/**	Browse column of the quantity to issue	*/
	private static final String COLUMN_MOVEMENT_QTY = "TEL_MovementQty";
	/**	Browse column of the quantity pending to issue	*/
	private static final String COLUMN_QTY_PENDING = "TEL_QtyPending";

	@Override
	protected String doIt() throws Exception {
		ProjectIssueGenerator generator = ProjectIssueGenerator.newInstance(getCtx(), get_TrxName())
				.withMovementDate(getMovementDate())
				.withDescription(getDescription());
		int created = 0;
		for (int timeExpenseLineId : getSelectionKeys()) {
			BigDecimal quantity = Optional.ofNullable(getSelectionAsBigDecimal(timeExpenseLineId, COLUMN_MOVEMENT_QTY))
					.orElse(BigDecimal.ZERO);
			ProjectIssueGenerator.validateQuantity(quantity,
					Optional.ofNullable(getSelectionAsBigDecimal(timeExpenseLineId, COLUMN_QTY_PENDING)).orElse(BigDecimal.ZERO));
			MTimeExpenseLine expenseLine = new MTimeExpenseLine(getCtx(), timeExpenseLineId, get_TrxName());
			if (expenseLine.getC_Project_ID() <= 0) {
				throw new AdempiereException("@C_Project_ID@ @NotFound@ - @S_TimeExpenseLine_ID@ " + expenseLine.getLine());
			}
			MTimeExpense expense = expenseLine.getParent();
			MProject project = new MProject(getCtx(), expenseLine.getC_Project_ID(), get_TrxName());
			MProjectLine projectLine = generator.getProjectLine(project, 0, expenseLine.getM_Product_ID(),
					expenseLine.getC_ProjectPhase_ID(), expenseLine.getC_ProjectTask_ID());
			//	Locator with stock in the warehouse of the report, else its default locator
			int locatorId = MStorage.getM_Locator_ID(expense.getM_Warehouse_ID(), expenseLine.getM_Product_ID(),
					0, quantity, get_TrxName());
			if (locatorId <= 0) {
				locatorId = expense.getM_Locator_ID();
			}
			String sourceDescription = Util.isEmpty(expenseLine.getDescription(), true)
					? expense.getDescription()
					: expenseLine.getDescription();
			MProjectIssue projectIssue = generator.createIssue(projectLine, locatorId, expenseLine.getM_Product_ID(),
					0, quantity, sourceDescription, 0, expenseLine.getS_TimeExpenseLine_ID());
			addLog(projectIssue.getC_ProjectIssue_ID(), projectIssue.getMovementDate(), projectIssue.getMovementQty(),
					projectIssue.getDocumentNo());
			created++;
		}
		return "@Created@ " + created;
	}
}
