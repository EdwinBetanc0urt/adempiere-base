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

import org.adempiere.core.domains.models.X_C_LandedCost;
import org.adempiere.engine.CostEngineFactory;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.PeriodClosedException;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.process.DocumentReversalEnabled;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * 	Landed Cost Document
 *  @author Jorg Janke
 *  @version $Id: MLandedCost.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 */
public class MLandedCost extends X_C_LandedCost implements DocAction, DocumentReversalEnabled, DocOptions
{
	private static final long serialVersionUID = 20261112L;

	public static final String DOCBASETYPE_LandedCost = "LCA";

	private String processMsg = null;
	private boolean justPrepared = false;
	private boolean reversal = false;
	private boolean voided = false;

	public static List<MLandedCost> getOfInvoice(Properties ctx, int invoiceId, String trxName)
	{
		return new Query(ctx, Table_Name, COLUMNNAME_C_Invoice_ID + "=?", trxName)
			.setParameters(invoiceId)
			.setOrderBy(COLUMNNAME_C_LandedCost_ID)
			.list();
	}

	public static List<MLandedCost> getOfReceipt(Properties ctx, int inOutId, String trxName)
	{
		return new Query(ctx, Table_Name, COLUMNNAME_M_InOut_ID + "=?", trxName)
			.setParameters(inOutId)
			.setOrderBy(COLUMNNAME_C_LandedCost_ID)
			.list();
	}

	public static String getBaseIssue(Properties ctx, MInOutLine iol, String distribution, BigDecimal base)
	{
		if (base.signum() != 0 || iol.getMovementQty().signum() == 0)
			return null;
		String key;
		if (LANDEDCOSTDISTRIBUTION_Volume.equals(distribution))
			key = "LandedCostNoVolumeError";
		else if (LANDEDCOSTDISTRIBUTION_Weight.equals(distribution))
			key = "LandedCostNoWeightError";
		else if (LANDEDCOSTDISTRIBUTION_Costs.equals(distribution))
			key = "LandedCostNoInvoiceError";
		else
			return null;
		MProduct product = iol.getProduct();
		String productLabel = (product != null ? product.getValue() + " - " + product.getName()
								: String.valueOf(iol.getM_Product_ID()))
			+ " (" + iol.getParent().getDocumentNo() + " / " + iol.getLine() + ")";
		return Msg.getMsg(ctx, key, new Object[]{productLabel});
	}

	public static String getZeroBaseMessage(Properties ctx, String distribution)
	{
		if (LANDEDCOSTDISTRIBUTION_Costs.equals(distribution))
			return Msg.getMsg(ctx, "LandedCostNoInvoiceError");
		else if (LANDEDCOSTDISTRIBUTION_Quantity.equals(distribution))
			return Msg.getMsg(ctx, "LandedCostZeroQtyError");
		else if (LANDEDCOSTDISTRIBUTION_Weight.equals(distribution))
			return Msg.getMsg(ctx, "LandedCostNoWeightError");
		else if (LANDEDCOSTDISTRIBUTION_Volume.equals(distribution))
			return Msg.getMsg(ctx, "LandedCostNoVolumeError");
		return "";
	}

	public MLandedCost (Properties ctx, int C_LandedCost_ID, String trxName)
	{
		super (ctx, C_LandedCost_ID, trxName);
		if (C_LandedCost_ID == 0)
		{
			setLandedCostDistribution (LANDEDCOSTDISTRIBUTION_Quantity);
			setDocStatus(DOCSTATUS_Drafted);
			setDocAction(DOCACTION_Complete);
			setProcessed(false);
			setPosted(false);
			setAmt(Env.ZERO);
		}
	}

	public MLandedCost (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}

	protected boolean beforeSave (boolean newRecord)
	{
		if (getC_DocType_ID() <= 0)
		{
			Optional<MDocType> docType = Arrays.stream(MDocType.getOfDocBaseType(getCtx(), DOCBASETYPE_LandedCost))
				.min((first, second) -> Boolean.compare(second.isDefault(), first.isDefault()));
			docType.ifPresent(type -> setC_DocType_ID(type.getC_DocType_ID()));
			if (getC_DocType_ID() <= 0)
				throw new AdempiereException("@C_DocType_ID@ @FillMandatory@");
		}
		if (getDateDoc() == null)
			setDateDoc(TimeUtil.getDay(System.currentTimeMillis()));
		if (getDateAcct() == null)
			setDateAcct(getDateDoc());
		if (getC_InvoiceLine_ID() != 0)
		{
			MInvoiceLine line = new MInvoiceLine(getCtx(), getC_InvoiceLine_ID(), get_TrxName());
			if (getC_Invoice_ID() == 0)
				setC_Invoice_ID(line.getC_Invoice_ID());
			if (getC_Currency_ID() == 0)
				setC_Currency_ID(line.getParent().getC_Currency_ID());
			if (getC_ConversionType_ID() == 0)
				setC_ConversionType_ID(line.getParent().getC_ConversionType_ID());
		}
		if (getM_Product_ID() == 0 && getM_InOut_ID() == 0 && getM_InOutLine_ID() == 0)
		{
			log.saveError("Error", Msg.parseTranslation(getCtx(),
				"@NotFound@ @M_Product_ID@ | @M_InOut_ID@ | @M_InOutLine_ID@"));
			return false;
		}
		if (getM_InOutLine_ID() != 0 && getM_Product_ID() != 0)
			setM_Product_ID(0);
		return true;
	}

	public String getDocumentInfo()
	{
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		return dt.getName() + " " + getDocumentNo();
	}

	public File createPDF ()
	{
		return null;
	}

	public File createPDF (File file)
	{
		return null;
	}

	public boolean processIt (String processAction)
	{
		processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}

	public boolean unlockIt()
	{
		log.info("unlockIt - " + toString());
		return true;
	}

	public boolean invalidateIt()
	{
		log.info("invalidateIt - " + toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}

	private List<MInOutLine> getScopeLines()
	{
		MInvoiceLandedCost draft = getC_InvoiceLandedCost_ID() > 0
			? new MInvoiceLandedCost(getCtx(), getC_InvoiceLandedCost_ID(), get_TrxName())
			: null;
		List<MInOutLine> lines = new java.util.ArrayList<>();
		for (MInOutLine iol : getReceiptLines())
		{
			if (draft != null && draft.isPreliminary())
			{
				boolean inScope = false;
				for (MOrderLine ol : draft.getScopeOrderLines())
				{
					if (ol.getC_OrderLine_ID() == iol.getC_OrderLine_ID())
					{
						inScope = true;
						break;
					}
				}
				if (!inScope)
					continue;
			}
			lines.add(iol);
		}
		return lines;
	}

	private List<MInOutLine> getReceiptLines()
	{
		List<MInOutLine> list = new java.util.ArrayList<>();
		if (getM_InOutLine_ID() != 0)
		{
			MInOutLine iol = new MInOutLine(getCtx(), getM_InOutLine_ID(), get_TrxName());
			if (!iol.isDescription() && iol.getM_Product_ID() != 0)
				list.add(iol);
			return list;
		}
		for (MInOutLine iol : new MInOut(getCtx(), getM_InOut_ID(), get_TrxName()).getLines())
		{
			if (iol.isDescription() || iol.getM_Product_ID() == 0)
				continue;
			if (getM_Product_ID() != 0 && getM_Product_ID() != iol.getM_Product_ID())
				continue;
			list.add(iol);
		}
		return list;
	}

	private void deleteAllocations()
	{
		DB.executeUpdateEx("DELETE FROM M_CostDetail WHERE EXISTS(SELECT 1 FROM C_LandedCostAllocation lca "
			+ "WHERE lca.C_LandedCostAllocation_ID = M_CostDetail.C_LandedCostAllocation_ID AND lca.C_LandedCost_ID = ?)",
			new Object[]{getC_LandedCost_ID()}, get_TrxName());
		DB.executeUpdateEx("DELETE FROM C_LandedCostAllocation WHERE C_LandedCost_ID = ?",
			new Object[]{getC_LandedCost_ID()}, get_TrxName());
	}

	private String distribute()
	{
		deleteAllocations();
		List<MInOutLine> lines = getScopeLines();
		if (lines.isEmpty())
			return Msg.getMsg(getCtx(), "LandedCostNoMatchError");
		LinkedHashSet<String> issues = new LinkedHashSet<>();
		BigDecimal total = Env.ZERO;
		for (MInOutLine iol : lines)
		{
			BigDecimal base = iol.getBase(getLandedCostDistribution());
			String baseIssue = getBaseIssue(getCtx(), iol, getLandedCostDistribution(), base);
			if (baseIssue != null)
				issues.add(baseIssue);
			total = total.add(base);
		}
		if (!issues.isEmpty())
			return Msg.getMsg(getCtx(), "LandedCostDistributionError", new Object[]{String.join("\n", issues)});
		if (total.signum() == 0)
			return getZeroBaseMessage(getCtx(), getLandedCostDistribution());
		int precision = MCurrency.getStdPrecision(getCtx(), getC_Currency_ID());
		for (MInOutLine iol : lines)
		{
			MLandedCostAllocation lca = new MLandedCostAllocation(this, getM_CostElement_ID());
			lca.setM_Product_ID(iol.getM_Product_ID());
			lca.setM_AttributeSetInstance_ID(iol.getM_AttributeSetInstance_ID());
			lca.setM_InOutLine_ID(iol.getM_InOutLine_ID());
			lca.setC_LandedCostType_ID(getC_LandedCostType_ID());
			BigDecimal base = iol.getBase(getLandedCostDistribution());
			lca.setBase(base);
			lca.setQty(iol.getMovementQty());
			if (base.signum() != 0)
				lca.setAmt(getAmt().multiply(base).divide(total, MathContext.DECIMAL128), precision);
			lca.saveEx();
		}
		allocateRounding();
		return "";
	}

	private void allocateRounding()
	{
		List<MLandedCostAllocation> allocations = MLandedCostAllocation.getOfLandedCost(getCtx(), getC_LandedCost_ID(), get_TrxName());
		MLandedCostAllocation largest = null;
		BigDecimal allocated = Env.ZERO;
		for (MLandedCostAllocation allocation : allocations)
		{
			if (largest == null || allocation.getAmt(true).abs().compareTo(largest.getAmt(true).abs()) > 0)
				largest = allocation;
			allocated = allocated.add(allocation.getAmt(true));
		}
		BigDecimal difference = getAmt().subtract(allocated);
		if (difference.signum() != 0 && largest != null)
		{
			largest.setAmt(largest.getAmt(true).add(difference));
			largest.saveEx();
		}
	}

	public String prepareIt()
	{
		log.info(toString());
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (processMsg != null)
			return DocAction.STATUS_Invalid;
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		if (!MPeriod.isOpen(getCtx(), getDateAcct(), dt.getDocBaseType(), getAD_Org_ID()))
		{
			processMsg = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}
		if (!isReversal())
		{
			String error = distribute();
			if (error != null && !error.isEmpty())
			{
				processMsg = error;
				return DocAction.STATUS_Invalid;
			}
		}
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (processMsg != null)
			return DocAction.STATUS_Invalid;
		justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}

	public boolean approveIt()
	{
		log.info("approveIt - " + toString());
		setIsApproved(true);
		return true;
	}

	public boolean rejectIt()
	{
		log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	}

	public String completeIt()
	{
		if (!justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (processMsg != null)
			return DocAction.STATUS_Invalid;
		if (!isApproved())
			approveIt();
		log.info(toString());
		setProcessed(true);
		saveEx();
		for (MLandedCostAllocation allocation : MLandedCostAllocation.getOfLandedCost(getCtx(), getC_LandedCost_ID(), get_TrxName()))
			CostEngineFactory.getCostEngine(getAD_Client_ID()).createCostDetailForLandedCostAllocation(allocation);
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		setDefiniteDocumentNo();
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}

	private void setDefiniteDocumentNo()
	{
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		if (dt.isOverwriteDateOnComplete())
			setDateDoc(new Timestamp(System.currentTimeMillis()));
		if (dt.isOverwriteSeqOnComplete())
		{
			String value = DB.getDocumentNo(getC_DocType_ID(), get_TrxName(), true);
			if (value != null)
				setDocumentNo(value);
		}
	}

	public boolean voidIt()
	{
		log.info(toString());
		if (DOCSTATUS_Closed.equals(getDocStatus())
			|| DOCSTATUS_Reversed.equals(getDocStatus())
			|| DOCSTATUS_Voided.equals(getDocStatus()))
		{
			processMsg = "Document Closed: " + getDocStatus();
			setDocAction(DOCACTION_None);
			return false;
		}
		if (DOCSTATUS_Drafted.equals(getDocStatus())
			|| DOCSTATUS_Invalid.equals(getDocStatus())
			|| DOCSTATUS_InProgress.equals(getDocStatus())
			|| DOCSTATUS_Approved.equals(getDocStatus()))
		{
			processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_VOID);
			if (processMsg != null)
				return false;
			deleteAllocations();
			setAmt(Env.ZERO);
			releaseDraft();
			processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_VOID);
			if (processMsg != null)
				return false;
			setDocAction(DOCACTION_None);
			return true;
		}
		boolean accrual = false;
		try
		{
			MPeriod.testPeriodOpen(getCtx(), getDateAcct(), DOCBASETYPE_LandedCost, getAD_Org_ID());
		}
		catch (PeriodClosedException e)
		{
			accrual = true;
		}
		voided = true;
		return accrual ? reverseAccrualIt() : reverseCorrectIt();
	}

	public boolean closeIt()
	{
		log.info(toString());
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_CLOSE);
		if (processMsg != null)
			return false;
		setDocAction(DOCACTION_None);
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_CLOSE);
		return processMsg == null;
	}

	public boolean reverseCorrectIt()
	{
		log.info(toString());
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (processMsg != null)
			return false;
		if (reverseIt(false) == null)
			return false;
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (processMsg != null)
			return false;
		setDocAction(DOCACTION_None);
		return true;
	}

	public boolean reverseAccrualIt()
	{
		log.info(toString());
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (processMsg != null)
			return false;
		if (reverseIt(true) == null)
			return false;
		processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (processMsg != null)
			return false;
		setDocAction(DOCACTION_None);
		return true;
	}

	@Override
	public MLandedCost reverseIt(boolean isAccrual)
	{
		Timestamp date = isAccrual ? TimeUtil.getDay(System.currentTimeMillis()) : getDateAcct();
		MLandedCost reverse = new MLandedCost(getCtx(), 0, get_TrxName());
		PO.copyValues(this, reverse);
		reverse.setDocumentNo(getDocumentNo() + "^");
		reverse.setDateDoc(date);
		reverse.setDateAcct(date);
		reverse.setAmt(getAmt().negate());
		reverse.setReversal(true);
		reverse.setProcessed(false);
		reverse.setPosted(false);
		reverse.setIsApproved(false);
		reverse.setDocStatus(DOCSTATUS_Drafted);
		reverse.setDocAction(DOCACTION_Complete);
		reverse.saveEx();
		for (MLandedCostAllocation allocation : MLandedCostAllocation.getOfLandedCost(getCtx(), getC_LandedCost_ID(), get_TrxName()))
		{
			MLandedCostAllocation copy = new MLandedCostAllocation(getCtx(), 0, get_TrxName());
			PO.copyValues(allocation, copy);
			copy.setC_LandedCost_ID(reverse.getC_LandedCost_ID());
			copy.setAmt(allocation.getAmt(true).negate());
			copy.saveEx();
		}
		if (!reverse.processIt(DOCACTION_Complete))
			throw new AdempiereException(reverse.getProcessMsg());
		String newStatus = voided ? DOCSTATUS_Voided : DOCSTATUS_Reversed;
		reverse.setDocStatus(newStatus);
		reverse.setDocAction(DOCACTION_None);
		reverse.setReversal_ID(getC_LandedCost_ID());
		reverse.saveEx();
		setReversal_ID(reverse.getC_LandedCost_ID());
		setDocStatus(newStatus);
		setDocAction(DOCACTION_None);
		saveEx();
		releaseDraft();
		voided = false;
		return reverse;
	}

	private void releaseDraft()
	{
		if (getC_InvoiceLandedCost_ID() <= 0 || reversal)
			return;
		MInvoiceLandedCost draft = new MInvoiceLandedCost(getCtx(), getC_InvoiceLandedCost_ID(), get_TrxName());
		if (draft.isGenerated())
		{
			draft.setIsGenerated(false);
			draft.saveEx();
		}
	}

	@Override
	public boolean isReversal()
	{
		return getReversal_ID() > 0 || reversal;
	}

	@Override
	public void setReversal(boolean isReversal)
	{
		this.reversal = isReversal;
	}

	public boolean reActivateIt()
	{
		log.info("reActivateIt - " + toString());
		return false;
	}

	public String getSummary()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getDocumentNo());
		if (getDescription() != null && !getDescription().isEmpty())
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}

	public String getProcessMsg()
	{
		return processMsg;
	}

	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}

	public BigDecimal getApprovalAmt()
	{
		return getAmt();
	}

	@Override
	public int customizeValidActions(String docStatus, Object processing,
		String orderType, String isSOTrx, int tableId,
		String[] docAction, String[] options, int index)
	{
		if (Table_ID == tableId)
		{
			if (docStatus.equals(DocumentEngine.STATUS_Drafted)
				|| docStatus.equals(DocumentEngine.STATUS_InProgress)
				|| docStatus.equals(DocumentEngine.STATUS_Invalid))
			{
				options[index++] = DocumentEngine.ACTION_Prepare;
				options[index++] = DocumentEngine.ACTION_Void;
			}
			else if (docStatus.equals(DocumentEngine.STATUS_Completed))
			{
				options[index++] = DocumentEngine.ACTION_Void;
				options[index++] = DocumentEngine.ACTION_Close;
			}
			else if (docStatus.equals(DocumentEngine.STATUS_Closed))
			{
				options[index++] = DocumentEngine.ACTION_None;
			}
		}
		return index;
	}

	public String toString ()
	{
		StringBuilder sb = new StringBuilder ("MLandedCost[");
		sb.append (get_ID())
			.append (",DocumentNo=").append (getDocumentNo())
			.append (",CostDistribution=").append (getLandedCostDistribution())
			.append (",M_CostElement_ID=").append (getM_CostElement_ID());
		if (getM_InOut_ID() != 0)
			sb.append (",M_InOut_ID=").append (getM_InOut_ID());
		if (getM_InOutLine_ID() != 0)
			sb.append (",M_InOutLine_ID=").append (getM_InOutLine_ID());
		sb.append ("]");
		return sb.toString ();
	}
}
