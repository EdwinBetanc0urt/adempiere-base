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

package org.solop.project.util;

import org.adempiere.core.domains.models.I_M_Transaction;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInOutLine;
import org.compiere.model.MLocator;
import org.compiere.model.MProduct;
import org.compiere.model.MProject;
import org.compiere.model.MProjectIssue;
import org.compiere.model.MProjectLine;
import org.compiere.model.MStorage;
import org.compiere.model.MTimeExpenseLine;
import org.compiere.model.MWarehouse;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.Env;
import org.compiere.util.Util;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Creates and completes project issues for the issue smart browsers,
 * linking each issue to the project line that receives the delivered quantity.
 */
public class ProjectIssueGenerator {

	private final Properties context;
	private final String transactionName;
	private Timestamp movementDate;
	private String description;

	private ProjectIssueGenerator(Properties context, String transactionName) {
		this.context = context;
		this.transactionName = transactionName;
	}

	public static ProjectIssueGenerator newInstance(Properties context, String transactionName) {
		return new ProjectIssueGenerator(context, transactionName);
	}

	/**
	 * Movement date of the issues, today when not set
	 */
	public ProjectIssueGenerator withMovementDate(Timestamp movementDate) {
		this.movementDate = movementDate;
		return this;
	}

	/**
	 * Description of the issues, it replaces the description of the source document
	 */
	public ProjectIssueGenerator withDescription(String description) {
		this.description = description;
		return this;
	}

	/**
	 * Validate the quantity to issue against the pending quantity of the source
	 */
	public static void validateQuantity(BigDecimal quantity, BigDecimal pendingQuantity) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new AdempiereException("@MovementQty@ <= 0");
		}
		if (pendingQuantity != null && quantity.compareTo(pendingQuantity) > 0) {
			throw new AdempiereException("@MovementQty@ > @QtyAvailable@ (" + pendingQuantity + ")");
		}
	}

	/**
	 * Get the project line of the product: the given one when it is valid, else the first open line of the project
	 * with the same product, phase and task, else a new line
	 * @param project project
	 * @param projectLineId suggested project line
	 * @param productId product
	 * @param projectPhaseId project phase
	 * @param projectTaskId project task
	 * @return project line
	 */
	public MProjectLine getProjectLine(MProject project, int projectLineId, int productId, int projectPhaseId, int projectTaskId) {
		if (projectLineId > 0) {
			MProjectLine projectLine = new MProjectLine(context, projectLineId, transactionName);
			if (projectLine.getC_Project_ID() == project.getC_Project_ID()) {
				return projectLine;
			}
		}
		StringBuilder whereClause = new StringBuilder("C_Project_ID=? AND M_Product_ID=? AND IsSummary='N'");
		List<Object> parameters = new ArrayList<>();
		parameters.add(project.getC_Project_ID());
		parameters.add(productId);
		if (projectPhaseId > 0) {
			whereClause.append(" AND C_ProjectPhase_ID=?");
			parameters.add(projectPhaseId);
		}
		if (projectTaskId > 0) {
			whereClause.append(" AND C_ProjectTask_ID=?");
			parameters.add(projectTaskId);
		}
		MProjectLine projectLine = new Query(context, MProjectLine.Table_Name, whereClause.toString(), transactionName)
				.setParameters(parameters)
				.setOnlyActiveRecords(true)
				.setOrderBy(MProjectLine.COLUMNNAME_Line)
				.first();
		if (projectLine != null) {
			return projectLine;
		}
		projectLine = new MProjectLine(project);
		projectLine.setM_Product_ID(productId);
		if (projectPhaseId > 0) {
			projectLine.setC_ProjectPhase_ID(projectPhaseId);
		}
		if (projectTaskId > 0) {
			projectLine.setC_ProjectTask_ID(projectTaskId);
		}
		projectLine.saveEx();
		return projectLine;
	}

	/**
	 * Create and complete a project issue
	 * @param projectLine project line that receives the issue
	 * @param locatorId locator of the issued product
	 * @param productId product
	 * @param attributeSetInstanceId attribute set instance, 0 to apply the material policy
	 * @param quantity quantity to issue
	 * @param sourceDescription description of the source document
	 * @return completed project issue
	 */
	public MProjectIssue createIssue(MProjectLine projectLine, int locatorId, int productId, int attributeSetInstanceId,
			BigDecimal quantity, String sourceDescription) {
		return createIssue(projectLine, locatorId, productId, attributeSetInstanceId, quantity, sourceDescription, 0, 0, 0);
	}

	/**
	 * Create and complete a project issue from a receipt or expense report line
	 * @param projectLine project line that receives the issue
	 * @param locatorId locator of the issued product
	 * @param productId product
	 * @param attributeSetInstanceId attribute set instance, 0 to apply the material policy
	 * @param quantity quantity to issue
	 * @param sourceDescription description of the source document
	 * @param inOutLineId receipt line
	 * @param timeExpenseLineId expense report line
	 * @return completed project issue
	 */
	public MProjectIssue createIssue(MProjectLine projectLine, int locatorId, int productId, int attributeSetInstanceId,
			BigDecimal quantity, String sourceDescription, int inOutLineId, int timeExpenseLineId) {
		return createIssue(projectLine, locatorId, productId, attributeSetInstanceId, quantity, sourceDescription,
				inOutLineId, timeExpenseLineId, 0);
	}

	/**
	 * Create and complete a project issue from a receipt or expense report line, optionally for a charge
	 * @param projectLine project line that receives the issue
	 * @param locatorId locator of the issued product, ignored for not storable products and charges
	 * @param productId product, 0 for a charge
	 * @param attributeSetInstanceId attribute set instance, 0 to apply the material policy
	 * @param quantity quantity to issue
	 * @param sourceDescription description of the source document
	 * @param inOutLineId receipt line
	 * @param timeExpenseLineId expense report line
	 * @param chargeId charge, 0 when the line has a product
	 * @return completed project issue
	 */
	public MProjectIssue createIssue(MProjectLine projectLine, int locatorId, int productId, int attributeSetInstanceId,
			BigDecimal quantity, String sourceDescription, int inOutLineId, int timeExpenseLineId, int chargeId) {
		//	A null quantity is treated as zero to avoid failures on the arithmetic and the document
		quantity = Optional.ofNullable(quantity).orElse(BigDecimal.ZERO);
		//	The locator is only needed for stocked products; services, resources and charges issue without one
		MProduct product = productId > 0 ? MProduct.get(context, productId) : null;
		boolean requiresLocator = product != null && product.isStocked();
		if (requiresLocator) {
			if (locatorId <= 0) {
				locatorId = getFallbackLocatorId(projectLine, inOutLineId, timeExpenseLineId, productId, attributeSetInstanceId, quantity);
			}
			if (locatorId <= 0) {
				throw new AdempiereException("@M_Locator_ID@ @NotFound@");
			}
			validateOnHand(locatorId, productId, attributeSetInstanceId, quantity);
		} else {
			locatorId = 0;
		}
		MProjectIssue projectIssue = new MProjectIssue(projectLine.getProject());
		projectIssue.setMandatory(locatorId, productId, quantity);
		projectIssue.setM_AttributeSetInstance_ID(attributeSetInstanceId);
		if (chargeId > 0) {
			projectIssue.setC_Charge_ID(chargeId);
		}
		projectIssue.setC_ProjectLine_ID(projectLine.getC_ProjectLine_ID());
		if (projectLine.getC_ProjectPhase_ID() > 0) {
			projectIssue.setC_ProjectPhase_ID(projectLine.getC_ProjectPhase_ID());
		}
		if (projectLine.getC_ProjectTask_ID() > 0) {
			projectIssue.setC_ProjectTask_ID(projectLine.getC_ProjectTask_ID());
		}
		if (inOutLineId > 0) {
			projectIssue.setM_InOutLine_ID(inOutLineId);
		}
		if (timeExpenseLineId > 0) {
			projectIssue.setS_TimeExpenseLine_ID(timeExpenseLineId);
		}
		if (movementDate != null) {
			projectIssue.setMovementDate(movementDate);
			projectIssue.setDateAcct(movementDate);
		}
		if (!Util.isEmpty(description, true)) {
			projectIssue.setDescription(description);
		} else if (!Util.isEmpty(sourceDescription, true)) {
			projectIssue.setDescription(sourceDescription);
		}
		projectIssue.saveEx();
		if (!projectIssue.processIt(DocAction.ACTION_Complete)) {
			throw new AdempiereException(projectIssue.getProcessMsg());
		}
		projectIssue.saveEx();
		return projectIssue;
	}

	/**
	 * Validate that the locator has the quantity on hand of the product at the movement date,
	 * based on the material transactions to avoid the delay of the storage queue
	 */
	private void validateOnHand(int locatorId, int productId, int attributeSetInstanceId, BigDecimal quantity) {
		MProduct product = MProduct.get(context, productId);
		if (product == null || !product.isStocked()) {
			return;
		}
		StringBuilder whereClause = new StringBuilder(I_M_Transaction.COLUMNNAME_M_Product_ID + "=? AND "
				+ I_M_Transaction.COLUMNNAME_M_Locator_ID + "=? AND " + I_M_Transaction.COLUMNNAME_MovementDate + "<=?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(productId);
		parameters.add(locatorId);
		parameters.add(movementDate != null ? movementDate : new Timestamp(System.currentTimeMillis()));
		if (attributeSetInstanceId > 0) {
			whereClause.append(" AND " + I_M_Transaction.COLUMNNAME_M_AttributeSetInstance_ID + "=?");
			parameters.add(attributeSetInstanceId);
		}
		BigDecimal qtyOnHand = new Query(context, I_M_Transaction.Table_Name, whereClause.toString(), transactionName)
				.setParameters(parameters)
				.aggregate(I_M_Transaction.COLUMNNAME_MovementQty, Query.AGGREGATE_SUM);
		if (qtyOnHand == null) {
			qtyOnHand = Env.ZERO;
		}
		if (qtyOnHand.compareTo(quantity) < 0) {
			throw new AdempiereException("@NotEnoughStocked@ " + product.getValue() + " - " + product.getName()
					+ ": @QtyAvailable@ " + qtyOnHand);
		}
	}

	/**
	 * Find an active locator for the warehouse, falling back to storage, default locator, or first active locator
	 */
	public int getWarehouseLocatorId(int warehouseId, int productId, int attributeSetInstanceId, BigDecimal quantity) {
		int locatorId = 0;
		if (warehouseId > 0 && productId > 0) {
			locatorId = MStorage.getM_Locator_ID(warehouseId, productId, attributeSetInstanceId, quantity, transactionName);
		}
		if (locatorId <= 0 && warehouseId > 0) {
			MWarehouse warehouse = MWarehouse.get(context, warehouseId);
			if (warehouse != null) {
				MLocator defaultLocator = MLocator.getDefault(warehouse);
				if (defaultLocator != null) {
					locatorId = defaultLocator.getM_Locator_ID();
				}
				if (locatorId <= 0) {
					locatorId = warehouse.getLocatorsAsList(false).stream()
							.filter(MLocator::isActive)
							.findFirst()
							.map(MLocator::getM_Locator_ID)
							.orElse(0);
				}
			}
		}
		return locatorId;
	}

	/**
	 * Resolve fallback locator when not provided by the source line
	 */
	private int getFallbackLocatorId(MProjectLine projectLine, int inOutLineId, int timeExpenseLineId,
			int productId, int attributeSetInstanceId, BigDecimal quantity) {
		int warehouseId = 0;
		if (inOutLineId > 0) {
			MInOutLine inOutLine = new MInOutLine(context, inOutLineId, transactionName);
			warehouseId = inOutLine.getM_Warehouse_ID();
			if (warehouseId <= 0 && inOutLine.getParent() != null) {
				warehouseId = inOutLine.getParent().getM_Warehouse_ID();
			}
		}
		if (warehouseId <= 0 && timeExpenseLineId > 0) {
			MTimeExpenseLine expenseLine = new MTimeExpenseLine(context, timeExpenseLineId, transactionName);
			if (expenseLine.getParent() != null) {
				warehouseId = expenseLine.getParent().getM_Warehouse_ID();
			}
		}
		if (warehouseId <= 0 && projectLine != null && projectLine.getProject() != null) {
			warehouseId = projectLine.getProject().getM_Warehouse_ID();
		}
		if (warehouseId <= 0) {
			warehouseId = Env.getContextAsInt(context, "#M_Warehouse_ID");
		}
		return getWarehouseLocatorId(warehouseId, productId, attributeSetInstanceId, quantity);
	}
}
