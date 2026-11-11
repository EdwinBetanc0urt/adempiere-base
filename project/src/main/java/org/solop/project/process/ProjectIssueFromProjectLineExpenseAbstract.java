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

import org.compiere.process.SvrProcess;

import java.sql.Timestamp;

/** Generated Process for (Project Issue From Expense Project Line)
 *  @author ADempiere (generated)
 *  @version Release 3.9.4
 */
public abstract class ProjectIssueFromProjectLineExpenseAbstract extends SvrProcess {
	/** Process Value 	*/
	private static final String VALUE_FOR_PROCESS = "C_ProjectIssue_FromProjectLineExpense";
	/** Process Name 	*/
	private static final String NAME_FOR_PROCESS = "Project Issue From Expense Project Line";
	/** Process Id 	*/
	private static final int ID_FOR_PROCESS = 2000281;
	/**	Parameter Name for Movement Date	*/
	public static final String MOVEMENTDATE = "MovementDate";
	/**	Parameter Name for Description	*/
	public static final String DESCRIPTION = "Description";
	/**	Parameter Name for Product (Not Stocked)	*/
	public static final String M_PRODUCT_ID = "M_Product_ID";
	/**	Parameter Name for Charge	*/
	public static final String C_CHARGE_ID = "C_Charge_ID";
	/**	Parameter Value for Movement Date	*/
	private Timestamp movementDate;
	/**	Parameter Value for Description	*/
	private String description;
	/**	Parameter Value for Product (Not Stocked)	*/
	private int productId;
	/**	Parameter Value for Charge	*/
	private int chargeId;

	@Override
	protected void prepare() {
		movementDate = getParameterAsTimestamp(MOVEMENTDATE);
		description = getParameterAsString(DESCRIPTION);
		productId = getParameterAsInt(M_PRODUCT_ID);
		chargeId = getParameterAsInt(C_CHARGE_ID);
	}

	/**	 Getter Parameter Value for Movement Date	*/
	protected Timestamp getMovementDate() {
		return movementDate;
	}

	/**	 Setter Parameter Value for Movement Date	*/
	protected void setMovementDate(Timestamp movementDate) {
		this.movementDate = movementDate;
	}

	/**	 Getter Parameter Value for Description	*/
	protected String getDescription() {
		return description;
	}

	/**	 Setter Parameter Value for Description	*/
	protected void setDescription(String description) {
		this.description = description;
	}

	/**	 Getter Parameter Value for Product (Not Stocked)	*/
	protected int getM_Product_ID() {
		return productId;
	}

	/**	 Setter Parameter Value for Product (Not Stocked)	*/
	protected void setM_Product_ID(int productId) {
		this.productId = productId;
	}

	/**	 Getter Parameter Value for Charge	*/
	protected int getC_Charge_ID() {
		return chargeId;
	}

	/**	 Setter Parameter Value for Charge	*/
	protected void setC_Charge_ID(int chargeId) {
		this.chargeId = chargeId;
	}

	/**	 Getter Parameter Value for Process ID	*/
	public static final int getProcessId() {
		return ID_FOR_PROCESS;
	}

	/**	 Getter Parameter Value for Process Value	*/
	public static final String getProcessValue() {
		return VALUE_FOR_PROCESS;
	}

	/**	 Getter Parameter Value for Process Name	*/
	public static final String getProcessName() {
		return NAME_FOR_PROCESS;
	}
}
