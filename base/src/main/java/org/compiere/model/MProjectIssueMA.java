/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.compiere.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.core.domains.models.X_C_ProjectIssueMA;
import org.compiere.util.DB;

/**
 * 	Project Issue Material Allocation
 */
public class MProjectIssueMA extends X_C_ProjectIssueMA
{
	private static final long serialVersionUID = 1L;

	/**
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param C_ProjectIssueMA_ID id
	 *	@param trxName trx
	 */
	public MProjectIssueMA (Properties ctx, int C_ProjectIssueMA_ID, String trxName)
	{
		super (ctx, C_ProjectIssueMA_ID, trxName);
	}	//	MProjectIssueMA

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trxName trx
	 */
	public MProjectIssueMA (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}	//	MProjectIssueMA

	/**
	 * 	Before Save: keep the reference to the project of the issue
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (getC_ProjectIssue_ID() > 0
				&& (newRecord || is_ValueChanged(COLUMNNAME_C_ProjectIssue_ID) || getC_Project_ID() <= 0))
		{
			int headerId = DB.getSQLValueEx(get_TrxName(),
					"SELECT C_Project_ID FROM C_ProjectIssue WHERE C_ProjectIssue_ID=?", getC_ProjectIssue_ID());
			setC_Project_ID(headerId);
		}
		return true;
	}	//	beforeSave

}	//	MProjectIssueMA
