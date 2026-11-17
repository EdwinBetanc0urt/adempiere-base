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
import org.compiere.model.MProduct;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MProjectLine;
import org.solop.project.util.ProjectIssueGenerator;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Issue to the project the selected expense project lines (not storable purchased or supply products).
 * Each issue is completed and linked to its project line with no inventory transaction.
 * The product or charge of the parameters, when given, replaces the product of the project line;
 * only one of them can be used, never both.
 */
public class ProjectIssueFromProjectLineExpense extends ProjectIssueFromProjectLineExpenseAbstract {

	/**	Browse column of the quantity to issue	*/
	private static final String COLUMN_MOVEMENT_QTY = "V_MovementQty";
	/**	Browse column of the quantity available to issue	*/
	private static final String COLUMN_QTY_AVAILABLE = "V_QtyAvailable";

	@Override
	protected String doIt() throws Exception {
		int parameterProductId = getM_Product_ID() > 0 ? getM_Product_ID() : 0;
		int parameterChargeId = getC_Charge_ID() > 0 ? getC_Charge_ID() : 0;
		//	The issue is done by a product or by a charge, never by both
		if (parameterProductId > 0 && parameterChargeId > 0) {
			throw new AdempiereException("@M_Product_ID@ / @C_Charge_ID@: @NotValid@");
		}
		//	The product of the parameter must be not storable, as the browse shows only not storable lines
		if (parameterProductId > 0) {
			MProduct parameterProduct = MProduct.get(getCtx(), parameterProductId);
			if (parameterProduct == null || parameterProduct.isStocked()) {
				throw new AdempiereException("@M_Product_ID@ @NotValid@ - @IsStocked@");
			}
		}
		ProjectIssueGenerator generator = ProjectIssueGenerator.newInstance(getCtx(), get_TrxName())
				.withMovementDate(getMovementDate())
				.withDescription(getDescription());
		int created = 0;
		for (int projectLineId : getSelectionKeys()) {
			BigDecimal quantity = Optional.ofNullable(getSelectionAsBigDecimal(projectLineId, COLUMN_MOVEMENT_QTY))
					.orElse(BigDecimal.ZERO);
			ProjectIssueGenerator.validateQuantity(quantity,
					Optional.ofNullable(getSelectionAsBigDecimal(projectLineId, COLUMN_QTY_AVAILABLE)).orElse(BigDecimal.ZERO));
			MProjectLine projectLine = new MProjectLine(getCtx(), projectLineId, get_TrxName());
			//	The product or charge of the parameters replaces the product of the line, else the line product is issued
			int productId = parameterProductId;
			int chargeId = parameterChargeId;
			if (productId <= 0 && chargeId <= 0) {
				productId = projectLine.getM_Product_ID();
			}
			MProjectIssue projectIssue = generator.createIssue(projectLine, 0, productId, 0, quantity,
					projectLine.getDescription(), 0, 0, chargeId);
			addLog(projectIssue.getC_ProjectIssue_ID(), projectIssue.getMovementDate(), projectIssue.getMovementQty(),
					projectIssue.getDocumentNo());
			created++;
		}
		return "@C_ProjectIssue_ID@: " + created;
	}
}
