/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import org.adempiere.core.domains.models.X_S_ResourceAssignment;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DisplayType;
import org.compiere.util.Msg;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.Properties;


/**
 *	Resource Assignment Model
 *	
 *  @author Jorg Janke
 *  @version $Id: MResourceAssignment.java,v 1.2 2006/07/30 00:51:03 jjanke Exp $
 */
public class MResourceAssignment extends X_S_ResourceAssignment
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3024759545660161137L;

	/**
	 * 	Stnadard Constructor
	 *	@param ctx
	 *	@param S_ResourceAssignment_ID
	 */
	public MResourceAssignment (Properties ctx, int S_ResourceAssignment_ID, String trxName)
	{
		super (ctx, S_ResourceAssignment_ID, trxName);
		p_info.setUpdateable(true);		//	default table is not updateable
		//	Default values
		if (S_ResourceAssignment_ID == 0)
		{
			setAssignDateFrom(new Timestamp(System.currentTimeMillis()));
			setQty(new BigDecimal(1.0));
			setName(".");
			setIsConfirmed(false);
		}
	}	//	MResourceAssignment

	/**
	 * 	Load Contsructor
	 *	@param ctx context
	 *	@param rs result set
	 */
	public MResourceAssignment (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MResourceAssignment

	/**
	 * 	Before Save
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (validatesAvailability()
				&& (newRecord || is_ValueChanged(COLUMNNAME_AssignDateFrom) || is_ValueChanged(COLUMNNAME_AssignDateTo) || is_ValueChanged(COLUMNNAME_S_Resource_ID))) {
			String whereClause = " ? < AssignDateTo " +
					" AND ? > AssignDateFrom " +
					" AND S_Resource_ID = ?" +
					" AND S_ResourceAssignment_ID <> ?";
			MResourceAssignment overlappingAssignment = new Query(getCtx(), Table_Name, whereClause, get_TrxName())
				.setParameters(getAssignDateFrom(), getAssignDateTo(), getS_Resource_ID(), getS_ResourceAssignment_ID())
				.setOnlyActiveRecords(true)
				.first();
			if (overlappingAssignment != null) {
				MResource resource = (MResource) getS_Resource();
				String timeFromFormated = DisplayType.getDateFormat(DisplayType.DateTime).format(getAssignDateFrom());
				String timeToFormated = DisplayType.getDateFormat(DisplayType.DateTime).format(getAssignDateTo());
				String message = Msg.parseTranslation(getCtx(), "@Assignment.Overlap@");
				String errorMessage = MessageFormat.format(message,
						resource.getName(),
						timeFromFormated,
						timeToFormated
				);
				throw new AdempiereException(errorMessage);
			}
		}
		return super.beforeSave(newRecord);
	}	//	beforeSave

	/**
	 * 	Does the resource type of this assignment validate availability?
	 * 	Resource types marked as not validating availability (IsValidateAvailability = N)
	 * 	allow overlapping assignments of the same resource (unlimited resources).
	 *	@return true unless the resource type explicitly disables availability validation
	 */
	private boolean validatesAvailability()
	{
		MResource resource = (MResource) getS_Resource();
		if (resource == null || resource.getS_ResourceType_ID() <= 0)
			return true;
		MResourceType resourceType = MResourceType.get(getCtx(), resource.getS_ResourceType_ID());
		return resourceType == null || resourceType.isValidateAvailability();
	}	//	validatesAvailability
	
	
	/**
	 * 	After Save
	 *	@param newRecord new
	 *	@param success success
	 *	@return true
	 */
	protected boolean afterSave (boolean newRecord, boolean success)
	{
		/*
		v_Description := :new.Name;
	IF (:new.Description IS NOT NULL AND LENGTH(:new.Description) > 0) THEN
		v_Description := v_Description || ' (' || :new.Description || ')';			
	END IF;
	
	-- Update Expense Line
	UPDATE S_TimeExpenseLine
	  SET  Description = v_Description,
		Qty = :new.Qty
	WHERE S_ResourceAssignment_ID = :new.S_ResourceAssignment_ID
	  AND (Description <> v_Description OR Qty <> :new.Qty);
	  
	-- Update Order Line
	UPDATE C_OrderLine
	  SET  Description = v_Description,
		QtyOrdered = :new.Qty
	WHERE S_ResourceAssignment_ID = :new.S_ResourceAssignment_ID
	  AND (Description <> v_Description OR QtyOrdered <> :new.Qty);

	-- Update Invoice Line
	UPDATE C_InvoiceLine
	  SET  Description = v_Description,
		QtyInvoiced = :new.Qty
	WHERE S_ResourceAssignment_ID = :new.S_ResourceAssignment_ID
	  AND (Description <> v_Description OR QtyInvoiced <> :new.Qty);
	  */
		return success;
	}	//	afterSave
	
	/**
	 *  String Representation
	 *  @return string
	 */
	public String toString()
	{
		StringBuffer sb = new StringBuffer ("MResourceAssignment[ID=");
		sb.append(get_ID())
			.append(",S_Resource_ID=").append(getS_Resource_ID())
			.append(",From=").append(getAssignDateFrom())
			.append(",To=").append(getAssignDateTo())
			.append(",Qty=").append(getQty())
			.append("]");
		return sb.toString();
	}   //  toString

	/**
	 * 	Before Delete
	 *	@return true if not confirmed
	 */
	protected boolean beforeDelete ()
	{
		//	 allow to delete, when not confirmed
		if (isConfirmed())
			return false;
		
		return true;
	}	//	beforeDelete
	
}	//	MResourceAssignment
