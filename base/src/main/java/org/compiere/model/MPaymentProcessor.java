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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.core.domains.models.I_C_PaymentProcessor;
import org.adempiere.core.domains.models.X_C_PaymentProcessor;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;


/**
 *  Payment Processor Model
 *
 *  @author Jorg Janke
 *  @version $Id: MPaymentProcessor.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 */
public class MPaymentProcessor extends X_C_PaymentProcessor {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1825454310856682804L;
	
	/**
	 * 	Get BankAccount & PaymentProcessor
	 *  @param tender optional Tender see TENDER_
	 *  @param creditCardCType optional CC Type see CC_
	 *  @param currencyId Currency (ignored)
	 *  @param paymentAmount Amount (ignored)
	 *  @return Array of BankAccount[0] & PaymentProcessor[1] or null
	 */
	protected static List<MPaymentProcessor> find (String tender, String creditCardCType, int currencyId, BigDecimal paymentAmount) {
		if (MPayment.TENDERTYPE_Cash.equals(tender)) {
			return null;
		}
		List<Object> parameters = new ArrayList<>();
		StringBuilder whereClause = new StringBuilder("(C_Currency_ID IS NULL OR C_Currency_ID=?)"
				+ " AND (MinimumAmt IS NULL OR MinimumAmt = 0 OR MinimumAmt <= ?)");
		parameters.add(currencyId);
		parameters.add(paymentAmount);
		if (MPayment.TENDERTYPE_DirectDeposit.equals(tender)) {
			whereClause.append(" AND AcceptDirectDeposit='Y'");
		} else if (MPayment.TENDERTYPE_DirectDebit.equals(tender)) {
			whereClause.append(" AND AcceptDirectDebit='Y'");
		} else if (MPayment.TENDERTYPE_Check.equals(tender)) {
			whereClause.append(" AND AcceptCheck='Y'");
		} else if (MPayment.CREDITCARDTYPE_ATM.equals(creditCardCType)) {
			whereClause.append(" AND AcceptATM='Y'");
		} else if (MPayment.CREDITCARDTYPE_Amex.equals(creditCardCType)) {
			whereClause.append(" AND AcceptAMEX='Y'");
		} else if (MPayment.CREDITCARDTYPE_Visa.equals(creditCardCType)) {
			whereClause.append(" AND AcceptVISA='Y'");
		} else if (MPayment.CREDITCARDTYPE_MasterCard.equals(creditCardCType)) {
			whereClause.append(" AND AcceptMC='Y'");
		} else if (MPayment.CREDITCARDTYPE_Diners.equals(creditCardCType)) {
			whereClause.append(" AND AcceptDiners='Y'");
		} else if (MPayment.CREDITCARDTYPE_Discover.equals(creditCardCType)) {
			whereClause.append(" AND AcceptDiscover='Y'");
		} else if (MPayment.CREDITCARDTYPE_PurchaseCard.equals(creditCardCType)) {
			whereClause.append(" AND AcceptCORPORATE='Y'");
		}
		List<MPaymentProcessor> list = new Query(Env.getCtx(), I_C_PaymentProcessor.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(COLUMNNAME_C_PaymentProcessor_ID)
				.list();
		//
		if (list.isEmpty()) {
			s_log.warning("find - not found - AD_Client_ID=" + Env.getAD_Client_ID(Env.getCtx())
					+ ", C_Currency_ID=" + currencyId + ", Amt=" + paymentAmount);
		} else {
			s_log.fine("find - #" + list.size() + " - AD_Client_ID=" + Env.getAD_Client_ID(Env.getCtx())
					+ ", C_Currency_ID=" + currencyId + ", Amt=" + paymentAmount);
		}
		return list;
	}   //  find

	/**	Static Logger	*/
	private static CLogger	s_log	= CLogger.getCLogger (MPaymentProcessor.class);

	/**	Store Payment Method (terminal) table	*/
	public static final String TABLENAME_C_PaymentMethod = "C_PaymentMethod";
	/**	Payment Processor Run table	*/
	public static final String TABLENAME_C_PaymentProcessorRun = "C_PaymentProcessorRun";

	/**
	 * Validate if a table has the C_PaymentProcessor_ID column
	 * @param tableName table name
	 * @return true if the column exists
	 */
	public static boolean isPaymentProcessorColumnAvailable(String tableName) {
		return MColumn.getColumn_ID(tableName, COLUMNNAME_C_PaymentProcessor_ID) > 0;
	}

	/**
	 * Get Payment Processor defined for a store payment method (terminal)
	 * @param ctx context
	 * @param paymentMethodId store payment method
	 * @param trxName transaction
	 * @return active processor or null
	 */
	public static MPaymentProcessor getFromPaymentMethod(Properties ctx, int paymentMethodId, String trxName) {
		if (paymentMethodId <= 0 || !isPaymentProcessorColumnAvailable(TABLENAME_C_PaymentMethod)) {
			return null;
		}
		int paymentProcessorId = DB.getSQLValue(trxName,
				"SELECT C_PaymentProcessor_ID FROM C_PaymentMethod WHERE C_PaymentMethod_ID = ?", paymentMethodId);
		return getActive(ctx, paymentProcessorId, trxName);
	}

	/**
	 * Get Payment Processor defined for payment: first the processor saved on payment,
	 * after the processor of store payment method (terminal)
	 * @param payment payment
	 * @return processor or null when it is not defined
	 */
	public static MPaymentProcessor getDefinedForPayment(MPayment payment) {
		if (payment == null) {
			return null;
		}
		if (payment.get_ColumnIndex(COLUMNNAME_C_PaymentProcessor_ID) >= 0) {
			MPaymentProcessor paymentProcessor = getActive(payment.getCtx(),
					payment.get_ValueAsInt(COLUMNNAME_C_PaymentProcessor_ID), payment.get_TrxName());
			if (paymentProcessor != null) {
				return paymentProcessor;
			}
		}
		return getFromPaymentMethod(payment.getCtx(), payment.getC_PaymentMethod_ID(), payment.get_TrxName());
	}

	/**
	 * Set Payment Processor on a record (payment, processor run) when the column exists
	 * @param entity record
	 * @param paymentProcessorId processor
	 */
	public static void setPaymentProcessorId(PO entity, int paymentProcessorId) {
		if (entity == null || paymentProcessorId <= 0
				|| entity.get_ColumnIndex(COLUMNNAME_C_PaymentProcessor_ID) < 0) {
			return;
		}
		entity.set_ValueOfColumn(COLUMNNAME_C_PaymentProcessor_ID, paymentProcessorId);
	}

	private static MPaymentProcessor getActive(Properties ctx, int paymentProcessorId, String trxName) {
		if (paymentProcessorId <= 0) {
			return null;
		}
		MPaymentProcessor paymentProcessor = new MPaymentProcessor(ctx, paymentProcessorId, trxName);
		if (paymentProcessor.getC_PaymentProcessor_ID() <= 0 || !paymentProcessor.isActive()) {
			return null;
		}
		return paymentProcessor;
	}

	
	/**************************************************************************
	 *	Payment Processor Model
	 * 	@param ctx context
	 * 	@param C_PaymentProcessor_ID payment processor
	 *	@param trxName transaction
	 */
	public MPaymentProcessor (Properties ctx, int C_PaymentProcessor_ID, String trxName)
	{
		super (ctx, C_PaymentProcessor_ID, trxName);
		if (C_PaymentProcessor_ID == 0)
		{
		//	setC_BankAccount_ID (0);		//	Parent
		//	setUserID (null);
		//	setPassword (null);
		//	setHostAddress (null);
		//	setHostPort (0);
			setCommission (Env.ZERO);
			setAcceptVisa (false);
			setAcceptMC (false);
			setAcceptAMEX (false);
			setAcceptDiners (false);
			setCostPerTrx (Env.ZERO);
			setAcceptCheck (false);
			setRequireVV (false);
			setAcceptCorporate (false);
			setAcceptDiscover (false);
			setAcceptATM (false);
			setAcceptDirectDeposit(false);
			setAcceptDirectDebit(false);
		//	setName (null);
		}
	}	//	MPaymentProcessor

	/**
	 *	Payment Processor Model
	 * 	@param ctx context
	 * 	@param rs result set
	 *	@param trxName transaction
	 */
	public MPaymentProcessor (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MPaymentProcessor

	/**
	 * 	String representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("MPaymentProcessor[")
			.append(get_ID ()).append("-").append(getName())
			.append ("]");
		return sb.toString ();
	}	//	toString

	/**
	 * 	Does Payment Processor accepts tender / CC
	 *	@param TenderType tender type
	 *	@param creditCardType credit card type
	 *	@return true if acceptes
	 */
	public boolean accepts (String TenderType, String creditCardType) {
        return (MPayment.TENDERTYPE_DirectDeposit.equals(TenderType) && isAcceptDirectDeposit())
                || (MPayment.TENDERTYPE_DirectDebit.equals(TenderType) && isAcceptDirectDebit())
                || (MPayment.TENDERTYPE_Check.equals(TenderType) && isAcceptCheck())
                //
                || (MPayment.CREDITCARDTYPE_ATM.equals(creditCardType) && isAcceptATM())
                || (MPayment.CREDITCARDTYPE_Amex.equals(creditCardType) && isAcceptAMEX())
                || (MPayment.CREDITCARDTYPE_PurchaseCard.equals(creditCardType) && isAcceptCorporate())
                || (MPayment.CREDITCARDTYPE_Diners.equals(creditCardType) && isAcceptDiners())
                || (MPayment.CREDITCARDTYPE_Discover.equals(creditCardType) && isAcceptDiscover())
                || (MPayment.CREDITCARDTYPE_MasterCard.equals(creditCardType) && isAcceptMC())
                || (MPayment.CREDITCARDTYPE_Visa.equals(creditCardType) && isAcceptVisa());
    }	//	accepts

}	//	MPaymentProcessor
