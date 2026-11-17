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

import org.adempiere.core.domains.models.I_C_ProjectIssueMA;
import org.adempiere.core.domains.models.X_C_ProjectIssue;
import org.adempiere.core.domains.models.X_C_ProjectIssueMA;
import org.adempiere.engine.IDocumentLine;
import org.adempiere.engine.ManualAllocation;
import org.adempiere.exceptions.PeriodClosedException;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.process.DocumentReversalEnabled;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.solop.queue.storage.StorageUpdate;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * 	Project Issue Model
 *
 *	@author Jorg Janke
 *	@version $Id: MProjectIssue.java,v 1.2 2006/07/30 00:51:02 jjanke Exp $
 */
public class MProjectIssue extends X_C_ProjectIssue implements IDocumentLine, DocAction, DocumentReversalEnabled
{

	/**
	 *
	 */
	private static final long serialVersionUID = 4714411434615096132L;

	/**
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param C_ProjectIssue_ID id
	 *	@param trxName transaction
	 */
	public MProjectIssue (Properties ctx, int C_ProjectIssue_ID, String trxName)
	{
		super (ctx, C_ProjectIssue_ID, trxName);
		if (C_ProjectIssue_ID == 0)
		{
		//	setC_Project_ID (0);
		//	setLine (0);
		//	setM_Locator_ID (0);
		//	setM_Product_ID (0);
		//	setMovementDate (new Timestamp(System.currentTimeMillis()));
			setMovementQty (Env.ZERO);
			setPosted (false);
			setProcessed (false);
			setDocStatus (DOCSTATUS_Drafted);
			setDocAction (DOCACTION_Complete);
		}
	}	//	MProjectIssue

	/**
	 * 	Load Constructor
	 * 	@param ctx context
	 * 	@param rs result set
	 *	@param trxName transaction
	 */
	public MProjectIssue (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MProjectIssue

	/**
	 * 	New Parent Constructor
	 *	@param project parent
	 */
	public MProjectIssue (MProject project)
	{
		this (project.getCtx(), 0, project.get_TrxName());
		setClientOrg(project.getAD_Client_ID(), project.getAD_Org_ID());
		setC_Project_ID (project.getC_Project_ID());	//	Parent
		setLine (getNextLine());
		m_parent = project;
		//
	//	setM_Locator_ID (0);
	//	setM_Product_ID (0);
		//
		setMovementDate (new Timestamp(System.currentTimeMillis()));
		setMovementQty (Env.ZERO);
		setPosted (false);
		setProcessed (false);
	}	//	MProjectIssue

	/**	Parent				*/
	private MProject	m_parent = null;
	/**	Process Message 			*/
	private String			m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean 		m_justPrepared = false;
	/**	Is a reversal				*/
	private boolean			m_reversal = false;

	/**
	 *	Get the next Line No
	 * 	@return next line no
	 */
	private int getNextLine()
	{
		return DB.getSQLValue(get_TrxName(),
			"SELECT COALESCE(MAX(Line),0)+10 FROM C_ProjectIssue WHERE C_Project_ID=?", getC_Project_ID());
	}	//	getLineFromProject

	/**
	 * 	Set Mandatory Values
	 *	@param M_Locator_ID locator
	 *	@param M_Product_ID product
	 *	@param MovementQty qty
	 */
	public void setMandatory (int M_Locator_ID, int M_Product_ID, BigDecimal MovementQty)
	{
		setM_Locator_ID (M_Locator_ID);
		setM_Product_ID (M_Product_ID);
		setMovementQty (MovementQty);
	}	//	setMandatory

	/**
	 * 	Get Parent
	 *	@return project
	 */
	public MProject getParent()
	{
		if (m_parent == null && getC_Project_ID() != 0)
			m_parent = new MProject (getCtx(), getC_Project_ID(), get_TrxName());
		return m_parent;
	}	//	getParent

	/**
	 * 	Before Save: set the document defaults
	 *	@param newRecord new
	 *	@return true
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (super.getC_DocType_ID() <= 0)
		{
			int docTypeId = MDocType.getDocType(MDocType.DOCBASETYPE_ProjectIssue, getAD_Org_ID());
			if (docTypeId > 0)
				setC_DocType_ID(docTypeId);
		}
		if (super.getDateAcct() == null)
			setDateAcct(getMovementDate());
		if (getDocStatus() == null)
			setDocStatus(DOCSTATUS_Drafted);
		if (getDocAction() == null)
			setDocAction(DOCACTION_Complete);
		return true;
	}	//	beforeSave

	/**************************************************************************
	 * 	Process Issue: complete the document
	 *	@return true if processed
	 */
	public boolean process()
	{
		if (!save())
			return false;
		if (!processIt(DOCACTION_Complete))
		{
			log.severe(getProcessMsg());
			return false;
		}
		saveEx();
		return true;
	}	//	process

	/**
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	@Override
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt

	/**
	 * 	Unlock Document.
	 * 	@return true if success
	 */
	@Override
	public boolean unlockIt()
	{
		log.info(toString());
		setProcessing(false);
		return true;
	}	//	unlockIt

	/**
	 * 	Invalidate Document
	 * 	@return true if success
	 */
	@Override
	public boolean invalidateIt()
	{
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}	//	invalidateIt

	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid)
	 */
	@Override
	public String prepareIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	A charge can be issued without a product
		if (getM_Product_ID() == 0 && getC_Charge_ID() <= 0)
		{
			m_processMsg = "@M_Product_ID@ @NotFound@";
			return DocAction.STATUS_Invalid;
		}
		//	The locator is only required for stocked products, as the receipt does for not storable products
		if (getM_Product_ID() > 0)
		{
			MProduct product = MProduct.get(getCtx(), getM_Product_ID());
			if (product != null && product.isStocked() && getM_Locator_ID() == 0)
			{
				m_processMsg = "@M_Locator_ID@ @NotFound@";
				return DocAction.STATUS_Invalid;
			}
		}

		//	Std Period open?
		MPeriod.testPeriodOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_ProjectIssue, getAD_Org_ID());

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt

	/**
	 * 	Approve Document
	 * 	@return true if success
	 */
	@Override
	public boolean approveIt()
	{
		log.info(toString());
		return true;
	}	//	approveIt

	/**
	 * 	Reject Approval
	 * 	@return true if success
	 */
	@Override
	public boolean rejectIt()
	{
		log.info(toString());
		return true;
	}	//	rejectIt

	/**
	 * 	Complete Document: create the material transactions of the issue
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	@Override
	public String completeIt()
	{
		//	Just prepare
		if (DOCACTION_Prepare.equals(getDocAction()))
		{
			setProcessed(false);
			return DocAction.STATUS_InProgress;
		}
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		log.info(toString());
		//	If not a stocked Item (service, resource or charge) there are no material transactions
		MProduct product = getM_Product_ID() > 0 ? MProduct.get (getCtx(), getM_Product_ID()) : null;
		if (product != null && product.isStocked())
		{
			//	Ignore the Material Policy when is Reverse Correction
			if (!isReversal())
				checkMaterialPolicy(product);
			createMaterialTransactions();
		}

		//	Refresh the delivered quantity of the project line; this issue is not processed yet in the database
		if (getC_ProjectLine_ID() > 0)
			MProjectLine.recalculateProjectLines(getCtx(),
				Collections.singletonMap(getC_ProjectLine_ID(), new BigDecimal[] {Env.ZERO, getMovementQty()}),
				get_TrxName(), Table_Name, false);

		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}

		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt

	/**
	 * 	Check Material Policy: create the allocations (C_ProjectIssueMA) when the issue has no attribute set instance
	 *	@param product product
	 */
	private void checkMaterialPolicy(MProduct product)
	{
		//	Manual allocation: validate and keep the allocations entered by the user
		if (ManualAllocation.isManualAllocation(this)) {
			ManualAllocation.validate(this, getM_Product_ID(), getM_AttributeSetInstance_ID(), getMovementQty(), getAllocations());
			return;
		}
		int no = deleteAllocations();
		if (no > 0)
			log.config("Delete old #" + no);

		if (getM_AttributeSetInstance_ID() != 0)
			return;

		MLocator locator = MLocator.get(getCtx(), getM_Locator_ID());
		MStorage[] storages = MStorage.getWarehouse(getCtx(), locator.getM_Warehouse_ID(), getM_Product_ID(), 0,
				null, MClient.MMPOLICY_FiFo.equals(product.getMMPolicy()), true, getM_Locator_ID(), get_TrxName());

		BigDecimal qtyToDeliver = getMovementQty();
		for (MStorage storage : storages)
		{
			BigDecimal allocationQty = storage.getQtyOnHand().compareTo(qtyToDeliver) >= 0
				? qtyToDeliver
				: storage.getQtyOnHand();
			createAllocation(storage.getM_AttributeSetInstance_ID(), allocationQty);
			qtyToDeliver = qtyToDeliver.subtract(allocationQty);
			log.fine("QtyToDeliver=" + qtyToDeliver);
			if (qtyToDeliver.signum() == 0)
				break;
		}

		//	No AttributeSetInstance found for remainder
		if (qtyToDeliver.signum() != 0)
		{
			MAttributeSet.validateAttributeSetInstanceMandatory(product, Table_ID, false, getM_AttributeSetInstance_ID());
			MAttributeSetInstance attributeSetInstance = MAttributeSetInstance.create(getCtx(), product, get_TrxName());
			createAllocation(attributeSetInstance.getM_AttributeSetInstance_ID(), qtyToDeliver);
		}
	}	//	checkMaterialPolicy

	/**
	 * 	Create the material transactions of the issue and add it to the storage queue:
	 * 	one transaction for each allocation when the issue has no attribute set instance
	 */
	private void createMaterialTransactions()
	{
		if (getM_AttributeSetInstance_ID() == 0)
		{
			getAllocations().forEach(allocation -> {
				MTransaction allocationTransaction = new MTransaction (getCtx(), getAD_Org_ID(),
					MTransaction.MOVEMENTTYPE_WorkOrder_,
					getM_Locator_ID(), getM_Product_ID(), allocation.getM_AttributeSetInstance_ID(),
					allocation.getMovementQty().negate(), getMovementDate(), get_TrxName());
				allocationTransaction.setC_ProjectIssue_ID(getC_ProjectIssue_ID());
				allocationTransaction.saveEx(get_TrxName());
			});
		} else {
			//	**	Create Material Transactions **
			MTransaction mTrx = new MTransaction (getCtx(), getAD_Org_ID(),
				MTransaction.MOVEMENTTYPE_WorkOrder_,
				getM_Locator_ID(), getM_Product_ID(), getM_AttributeSetInstance_ID(),
				getMovementQty().negate(), getMovementDate(), get_TrxName());
			mTrx.setC_ProjectIssue_ID(getC_ProjectIssue_ID());
			mTrx.saveEx(get_TrxName());
		}

		//	Add to Storage Queue
		StorageUpdate.addDocumentToQueue(this);
	}	//	createMaterialTransactions

	/**
	 * 	Get the allocations (C_ProjectIssueMA) of the issue
	 *	@return allocations
	 */
	public List<X_C_ProjectIssueMA> getAllocations()
	{
		return new Query(getCtx(), I_C_ProjectIssueMA.Table_Name,
				I_C_ProjectIssueMA.COLUMNNAME_C_ProjectIssue_ID + "=?", get_TrxName())
				.setClient_ID()
				.setParameters(getC_ProjectIssue_ID())
				.list();
	}	//	getAllocations

	/**
	 * 	Create an allocation (C_ProjectIssueMA) for the issue
	 *	@param attributeSetInstanceId attribute set instance
	 *	@param movementQty quantity
	 */
	private void createAllocation(int attributeSetInstanceId, BigDecimal movementQty)
	{
		MProjectIssueMA allocation = new MProjectIssueMA(getCtx(), 0, get_TrxName());
		allocation.setAD_Org_ID(getAD_Org_ID());
		allocation.setC_ProjectIssue_ID(getC_ProjectIssue_ID());
		allocation.setM_AttributeSetInstance_ID(attributeSetInstanceId);
		allocation.setMovementQty(movementQty);
		allocation.saveEx();
		log.fine(allocation.toString());
	}	//	createAllocation

	/**
	 * 	Delete the allocations (C_ProjectIssueMA) of the issue
	 *	@return number of deleted allocations
	 */
	private int deleteAllocations()
	{
		return DB.executeUpdateEx("DELETE FROM C_ProjectIssueMA WHERE C_ProjectIssue_ID=?",
				new Object[]{getC_ProjectIssue_ID()}, get_TrxName());
	}	//	deleteAllocations

	/**
	 * 	Void Document: a not completed issue is set to zero, a completed one is reversed
	 * 	@return true if success
	 */
	@Override
	public boolean voidIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		if (DOCSTATUS_Closed.equals(getDocStatus())
			|| DOCSTATUS_Reversed.equals(getDocStatus())
			|| DOCSTATUS_Voided.equals(getDocStatus()))
		{
			m_processMsg = "Document Closed: " + getDocStatus();
			setDocAction(DOCACTION_None);
			return false;
		}

		//	Not Processed
		if (DOCSTATUS_Drafted.equals(getDocStatus())
			|| DOCSTATUS_Invalid.equals(getDocStatus())
			|| DOCSTATUS_InProgress.equals(getDocStatus())
			|| DOCSTATUS_Approved.equals(getDocStatus())
			|| DOCSTATUS_NotApproved.equals(getDocStatus()))
		{
			BigDecimal oldMovementQty = getMovementQty();
			if (oldMovementQty.signum() != 0)
			{
				setMovementQty(Env.ZERO);
				addDescription(Msg.getMsg(getCtx(), "Voided") + " (" + oldMovementQty + ")");
			}
		}
		else
		{
			boolean isAccrual = false;
			try
			{
				MPeriod.testPeriodOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_ProjectIssue, getAD_Org_ID());
			}
			catch (PeriodClosedException periodClosedException)
			{
				isAccrual = true;
			}
			return isAccrual ? reverseAccrualIt() : reverseCorrectIt();
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;

		setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	}	//	voidIt

	/**
	 * 	Close Document.
	 * 	@return true if success
	 */
	@Override
	public boolean closeIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;

		setProcessed(true);
		setDocAction(DOCACTION_None);

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_CLOSE);
		return m_processMsg == null;
	}	//	closeIt

	/**
	 * 	Create the reversal of the issue: a copy with the negated quantity and allocations
	 *	@param isAccrual true to reverse on the login date, false on the movement date
	 *	@return reversal issue or null
	 */
	@Override
	public MProjectIssue reverseIt(boolean isAccrual)
	{
		Timestamp currentDate = new Timestamp(System.currentTimeMillis());
		Optional<Timestamp> loginDateOptional = Optional.ofNullable(Env.getContextAsDate(getCtx(), "#Date"));
		Timestamp reversalDate = isAccrual ? loginDateOptional.orElse(currentDate) : getDateAcct();
		MPeriod.testPeriodOpen(getCtx(), reversalDate, MDocType.DOCBASETYPE_ProjectIssue, getAD_Org_ID());

		//	Deep Copy
		MProjectIssue reversal = new MProjectIssue(getCtx(), 0, get_TrxName());
		copyValues(this, reversal, getAD_Client_ID(), getAD_Org_ID());
		reversal.set_ValueNoCheck(COLUMNNAME_DocumentNo, null);
		reversal.setDocStatus(DOCSTATUS_Drafted);
		reversal.setDocAction(DOCACTION_Complete);
		reversal.setLine(reversal.getNextLine());
		if (isAccrual)
			reversal.setMovementDate(reversalDate);
		reversal.setDateAcct(reversalDate);
		reversal.setMovementQty(getMovementQty().negate());
		reversal.setPosted(false);
		reversal.setProcessing(false);
		reversal.setProcessed(false);
		reversal.addDescription("{->" + getDocumentNo() + ")");
		reversal.setReversal_ID(getC_ProjectIssue_ID());
		reversal.setReversal(true);
		//	Set Document No from flag
		MDocType docType = MDocType.get(getCtx(), getC_DocType_ID());
		if (docType.isCopyDocNoOnReversal())
			reversal.setDocumentNo(getDocumentNo() + Msg.getMsg(getCtx(), "^"));
		reversal.saveEx();

		//	Reverse the allocations
		getAllocations().forEach(allocation -> reversal.createAllocation(
				allocation.getM_AttributeSetInstance_ID(), allocation.getMovementQty().negate()));

		if (!reversal.processIt(DOCACTION_Complete))
		{
			m_processMsg = "Reversal ERROR: " + reversal.getProcessMsg();
			return null;
		}
		reversal.closeIt();
		reversal.setDocStatus(DOCSTATUS_Reversed);
		reversal.setDocAction(DOCACTION_None);
		reversal.saveEx();
		m_processMsg = reversal.getDocumentNo();

		//	Update Reversed (this)
		addDescription("(" + reversal.getDocumentNo() + "<-)");
		setReversal_ID(reversal.getC_ProjectIssue_ID());
		setProcessed(true);
		setDocStatus(DOCSTATUS_Reversed);	//	may come from void
		setDocAction(DOCACTION_None);
		return reversal;
	}	//	reverseIt

	/**
	 * 	Reverse Correction
	 * 	@return true if success
	 */
	@Override
	public boolean reverseCorrectIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;

		MProjectIssue reversal = reverseIt(false);
		if (reversal == null)
			return false;

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (m_processMsg != null)
			return false;

		m_processMsg = reversal.getDocumentNo();
		return true;
	}	//	reverseCorrectIt

	/**
	 * 	Reverse Accrual
	 * 	@return true if success
	 */
	@Override
	public boolean reverseAccrualIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;

		MProjectIssue reversal = reverseIt(true);
		if (reversal == null)
			return false;

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;

		m_processMsg = reversal.getDocumentNo();
		return true;
	}	//	reverseAccrualIt

	/**
	 * 	Re-activate - not supported
	 * 	@return false
	 */
	@Override
	public boolean reActivateIt()
	{
		log.info(toString());
		m_processMsg = "@NotSupported@";
		return false;
	}	//	reActivateIt

	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription

	@Override
	public boolean isReversal()
	{
		return m_reversal;
	}	//	isReversal

	@Override
	public void setReversal(boolean isReversal)
	{
		m_reversal = isReversal;
	}	//	setReversal

	@Override
	public String getSummary()
	{
		MProduct product = MProduct.get (getCtx(), getM_Product_ID());
		StringBuilder summary = new StringBuilder();
		if (getDocumentNo() != null)
			summary.append(getDocumentNo()).append(": ");
		if (product != null)
			summary.append(product.getName()).append(" ");
		summary.append(getMovementQty());
		if (getDescription() != null && getDescription().length() > 0)
			summary.append(" - ").append(getDescription());
		return summary.toString();
	}	//	getSummary

	@Override
	public String getDocumentInfo()
	{
		MDocType docType = MDocType.get(getCtx(), getC_DocType_ID());
		return docType.getName() + " " + getDocumentNo();
	}	//	getDocumentInfo

	@Override
	public File createPDF ()
	{
		return null;
	}	//	createPDF

	@Override
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg

	@Override
	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}	//	getDoc_User_ID

	@Override
	public BigDecimal getApprovalAmt()
	{
		return Env.ZERO;
	}	//	getApprovalAmt

	/**
	 * 	Get Document Type: the one of the issue or the default for project issues
	 *	@return document type
	 */
	@Override
	public int getC_DocType_ID() {
		int docTypeId = super.getC_DocType_ID();
		if (docTypeId > 0)
			return docTypeId;
		return MDocType.getDocType(MDocType.DOCBASETYPE_ProjectIssue, getAD_Org_ID());
	}

	@Override
	public int getM_LocatorTo_ID() {
		return 0;
	}

	@Override
	public int getM_AttributeSetInstanceTo_ID() {
		return 0;
	}

	/**
	 * 	Get Accounting Date: the one of the issue or the movement date
	 *	@return accounting date
	 */
	@Override
	public Timestamp getDateAcct() {
		Timestamp dateAcct = super.getDateAcct();
		if (dateAcct != null)
			return dateAcct;
		return getMovementDate();
	}

	@Override
	public boolean isSOTrx() {
		return false;
	}

	@Override
	public int getReversalLine_ID() {
		return getReversal_ID();
	}

	@Override
	public BigDecimal getPriceActual() {
		return BigDecimal.ZERO;
	}

	@Override
	public IDocumentLine getReversalDocumentLine() {
		if (getReversal_ID() <= 0)
			return null;
		return new MProjectIssue(getCtx(), getReversal_ID(), get_TrxName());
	}

	@Override
	public BigDecimal getPriceActualCurrency() {
		return BigDecimal.ZERO;
	}

	@Override
	public int getC_Currency_ID ()
	{
		MClient client  = MClient.get(getCtx());
		return client.getC_Currency_ID();
	}

	@Override
	public int getC_ConversionType_ID()
	{
		return  MConversionType.getDefault(getAD_Client_ID());
	}

	@Override
	public boolean isReversalParent() {
		return getReversal_ID() > 0 && getC_ProjectIssue_ID() < getReversal_ID();
	}


}	//	MProjectIssue
