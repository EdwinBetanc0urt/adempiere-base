/**
 * 
 */
package org.adempiere.engine;

import org.compiere.model.*;
import org.compiere.util.Env;
import org.compiere.util.Msg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author victor.perez@e-evolution.com, www.e-evolution.com
 * @author Systemhaus Westfalia SusanneCalderon <susanne.de.calderon@westfalia-it.com>
 *    <li> Set M_MatchInv_ID and M_MatchPO_ID in Costdetail</>
 *    https://github.com/adempiere/adempiere/issues/1918
 * 
 */
public class AverageInvoiceCostingMethod extends AbstractCostingMethod
		implements ICostingMethod {

	private boolean isReceiptLandedCost = false;
	private static final BigDecimal RESIDUAL_TOLERANCE = new BigDecimal("0.01");

	/**
	 * In a vendor receipt, a Landed Cost element contributes no amount of its own: the amount
	 * comes from the C_LandedCostAllocation rows, which are already accumulated into CumulatedAmt.
	 * Charging CostAmt to the receipt line as well makes the landed cost be counted twice when it
	 * is read back (CumulatedAmt + CostAmt), and every later outgoing transaction consumes at
	 * double the unit cost.
	 *
	 * This is the same condition {@link #isReceiptLandedCost} evaluates, but as a method: that flag
	 * is assigned in a branch that is not reached when the receipt shares its M_Transaction_ID with
	 * its own allocations, because the adjustment is zero and calculate() returns early.
	 *
	 * @return true when the current transaction is a vendor receipt and the cost element is a
	 * Landed Cost element
	 */
	private boolean isReceiptOfLandedCostElement() {
		return transaction != null
				&& MTransaction.MOVEMENTTYPE_VendorReceipts.equals(transaction.getMovementType())
				&& MCostElement.COSTELEMENTTYPE_LandedCost.equals(MCostElement
						.get(transaction.getCtx(), dimension.getM_CostElement_ID()).getCostElementType());
	}

    /**
     * Constructor for Cost Engine
     * @param accountSchema
     * @param transaction
     * @param model
     * @param dimension
     * @param costThisLevel
     * @param costLowLevel
     * @param isSalesTransaction
     */
	public void setCostingMethod(MAcctSchema accountSchema, MTransaction transaction, IDocumentLine model,
                                 MCost dimension, BigDecimal costThisLevel,
                                 BigDecimal costLowLevel, Boolean isSalesTransaction) {
		if (transaction != null 
				&& dimension != null)
		isReceiptLandedCost = false;
		this.accountSchema = accountSchema;
		this.transaction = transaction;
		this.dimension = dimension;
		this.costThisLevel = (costThisLevel == null ? Env.ZERO : costThisLevel);
		this.costLowLevel = (costLowLevel == null ? Env.ZERO : costLowLevel);
		this.isSalesTransaction = isSalesTransaction;
		this.model = model;
		this.costingLevel = MProduct.get(this.transaction.getCtx(), this.transaction.getM_Product_ID())
				.getCostingLevel(accountSchema, transaction.getAD_Org_ID());
		// find if this transaction exist into cost detail
		this.costDetail = MCostDetail.getByTransaction(this.model, this.transaction,
				this.accountSchema.getC_AcctSchema_ID(), this.dimension.getM_CostType_ID(),
				this.dimension.getM_CostElement_ID());

        this.dateAccounting = model.getDateAcct();
        this.movementQuantity = transaction.getMovementQty();
	}

	public void calculate() {
		// try find the last cost detail transaction
		lastCostDetail = MCostDetail.getLastTransaction(model, transaction,
				accountSchema.getC_AcctSchema_ID(), dimension.getM_CostType_ID(),
				dimension.getM_CostElement_ID(),dateAccounting,
				costingLevel);

		// If model is reversal then no calculate cost
		//Validate if model have a reverses and processing of reverse
		if (model.getReversalLine_ID() > 0 && costDetail == null && !model.isReversalParent())
			return;
		else if( costDetail != null && costDetail.isReversal() && model.getReversalLine_ID() > 0)
	    {
            setReversalCostDetail();
            return;
	    }

		// created a new instance cost detail to process calculated cost
		if (lastCostDetail == null) {
			lastCostDetail = new MCostDetail(transaction,
					accountSchema.getC_AcctSchema_ID(), dimension.getM_CostType_ID(),
					dimension.getM_CostElement_ID(), Env.ZERO, Env.ZERO,
					Env.ZERO, transaction.get_TrxName());
			lastCostDetail.setDateAcct(dateAccounting);
		}
			
		BigDecimal quantityOnHand = getNewAccumulatedQuantity(lastCostDetail);
		
		// The cost detail was created before then is necessary to update cost by
		// generate adjustment
		if (transaction.getM_Transaction_ID() == lastCostDetail.getM_Transaction_ID()) {
			if (model instanceof MMatchInv
					|| model instanceof MLandedCostAllocation)
				movementQuantity = Env.ZERO;
			
			//Processing provision of purchase cost  
			//Provision is calculated when the last cost detail  is a material receipt and not exist of invoice line
			//if an invoice line exist for this cost detail then an invoice line was processed for this material receipt 
			//and not exist different between purchase cost and invoice cost, this logic was implemented to prevent 
			//that a provision of purchase cost decreases more than one times in a cost adjustment
			BigDecimal provisionOfPurchaseCost = BigDecimal.ZERO;
			BigDecimal provisionOfPurchaseCostLL = BigDecimal.ZERO;
            // Quantity accumulated from last cost transaction
            accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(
                    movementQuantity);

			if (model instanceof MMatchInv)
			{
				if (lastCostDetail.getM_MatchInv_ID() != 0 || lastCostDetail.getC_LandedCostAllocation_ID() !=0)
				{
					StringBuffer whereClause = new StringBuffer();
					whereClause.append(" M_CostType_ID=" + dimension.getM_CostType_ID());
					whereClause.append(" AND M_CostElement_ID=" + dimension.getM_CostElement_ID());
					whereClause.append(" AND M_MatchInv_ID is null and C_LandedCostAllocation_ID is null");
					whereClause.append(" AND M_InOutline_ID=?");
					MCostDetail receiptCostDetail = MCostDetail.get(model.getCtx(),whereClause.toString(),
							((MMatchInv) model).getM_InOutLine_ID(),
							getReceiptAttributeSetInstanceId(), dimension.getC_AcctSchema_ID(), model.get_TrxName());
					if (receiptCostDetail!=null) {
						provisionOfPurchaseCost = receiptCostDetail.getCostAmt().divide(receiptCostDetail.getQty()).multiply(model.getMovementQty());//receiptCostDetail.getCostAmt();
						provisionOfPurchaseCostLL =  receiptCostDetail.getCostAmtLL().divide(receiptCostDetail.getQty()).multiply(model.getMovementQty());
					}
				}
				else
				{
					if (lastCostDetail!=null) {
						provisionOfPurchaseCost = lastCostDetail.getCostAmt().divide(lastCostDetail.getQty()).multiply(model.getMovementQty());//receiptCostDetail.getCostAmt();
						provisionOfPurchaseCostLL =  lastCostDetail.getCostAmtLL().divide(lastCostDetail.getQty()).multiply(model.getMovementQty());
					}
				}
//				MMatchInv iMatch =  (MMatchInv) model;
				//lastCostDetail.setC_InvoiceLine_ID(iMatch.getC_InvoiceLine_ID());
				//lastCostDetail.saveEx();
                // reset the accumulated quantity with last cost detail
                if (lastCostDetail != null && lastCostDetail.getM_CostDetail_ID() > 0)
                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail);
			}
			adjustCost = model.getMovementQty().multiply(costThisLevel).subtract(provisionOfPurchaseCost);
			adjustCostLowerLevel =  model.getMovementQty().multiply(costLowLevel).subtract(provisionOfPurchaseCostLL);

			
			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
			accumulatedAmount = accumulatedAmount.add(adjustCost);//accumulatedAmount = accumulatedQuantity.signum() > 0 ? accumulatedAmount.add(adjustCost) : accumulatedAmount.add(adjustCost.negate());
			alignAmountWithQuantity();
			
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
			accumulatedAmountLowerLevel =  accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(adjustCostLowerLevel) : 
				accumulatedAmountLowerLevel.add(adjustCostLowerLevel.negate());
					
			
			currentCostPrice = accumulatedAmount.divide(
					accumulatedQuantity.signum() != 0 ? accumulatedQuantity
							: BigDecimal.ONE, accountSchema.getCostingPrecision(),
							RoundingMode.HALF_UP);
			currentCostPriceLowerLevel = accumulatedAmountLowerLevel.divide(accumulatedQuantity
					.signum() != 0 ? accumulatedQuantity : BigDecimal.ONE, accountSchema
					.getCostingPrecision(), RoundingMode.HALF_UP);

			if(adjustCost.add(adjustCostLowerLevel).signum() == 0)
				return;
			// validation when the cost detail is reprocess
			if (costDetail == null)
				return;
			
			// reset with the current values
			costDetail.setCostAdjustment(adjustCost);
			costDetail.setAmt(costDetail.getCostAmt().add(costDetail.getCostAdjustment()));
			costDetail.setCostAdjustmentLL(adjustCostLowerLevel);
			costDetail.setAmtLL(costDetail.getCostAmtLL().add(costDetail.getCostAdjustmentLL()));

			updateAmountCost();

			return;
		}
		
		// calculated costing
		if (transaction.getMovementType().endsWith("+"))
		{
			//Project of cost if a cost was entry in zero, the inventory is revalued using the fist cost
			//Example ; Quantity On hand 2.00 , Total Cost : 0.00 , Transaction Quantity 4.00 , Cost Total Transaction 17.8196
			//cost This Level = ( (17.8196 / 4)  * 6 ) / 4 | (costThisLevel * costThisLevel) / lastCostDetail.getQty()
			
			//Detect Inventory with zero value
			//the On hand is different zero and inventory values is zero then
			if (quantityOnHand.signum() != 0
			&& getNewAccumulatedAmount(lastCostDetail).signum() == 0
			&& costThisLevel.signum() != 0
			)
			{				
				adjustCost = quantityOnHand.
								add(movementQuantity)
								.multiply(costThisLevel)
								.subtract(costThisLevel
								.multiply(movementQuantity));
			} // Logic to calculate adjustment when inventory is negative
			else if (quantityOnHand.add(movementQuantity).signum() < 0
			&& getNewCurrentCostPrice(lastCostDetail, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP).signum() != 0
			&& costThisLevel.signum() == 0  )
			{
				currentCostPrice = getNewCurrentCostPrice(lastCostDetail, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP);
				adjustCost = currentCostPrice.multiply(movementQuantity).abs();
			}
            // If period is not open then an adjustment cost is create based on quantity on hand of attribute instance
            // the amount difference is apply to adjustment cost account, the reason is because is import distribute
            // proportionally
			if (model instanceof MLandedCostAllocation || model instanceof MMatchInv)
			{
				BigDecimal provisionOfPurchaseCost = BigDecimal.ZERO;
				BigDecimal provisionOfPurchaseCostLL = BigDecimal.ZERO;
	            // Quantity accumulated from last cost transaction
	            accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(
	                    movementQuantity);

				if (model instanceof MMatchInv)
				{
					StringBuffer whereClause = new StringBuffer();
					whereClause.append(" M_CostType_ID=" + dimension.getM_CostType_ID());
					whereClause.append(" AND M_CostElement_ID=" + dimension.getM_CostElement_ID());
					whereClause.append(" AND M_MatchInv_ID is null and C_LandedCostAllocation_ID is null");
					whereClause.append(" AND M_InOutline_ID=?");
					whereClause.append(" AND Qty <> 0");
					MCostDetail receiptCostDetail = MCostDetail.get(model.getCtx(),whereClause.toString(),
							((MMatchInv) model).getM_InOutLine_ID(),
							getReceiptAttributeSetInstanceId(), dimension.getC_AcctSchema_ID(), model.get_TrxName());
					if (receiptCostDetail != null && Optional.ofNullable(model.getMovementQty()).orElse(Env.ZERO).compareTo(Env.ZERO) > 0) {
						provisionOfPurchaseCost = receiptCostDetail.getCostAmt().divide(receiptCostDetail.getQty()).multiply(model.getMovementQty());//receiptCostDetail.getCostAmt();
						provisionOfPurchaseCostLL =  receiptCostDetail.getCostAmtLL().divide(receiptCostDetail.getQty()).multiply(model.getMovementQty());
					}
	                // reset the accumulated quantity with last cost detail
	                if (lastCostDetail != null && lastCostDetail.getM_CostDetail_ID() > 0)
	                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail);
				}else {
					if (lastCostDetail != null && lastCostDetail.getM_CostDetail_ID() > 0)
	                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail);
				}
				adjustCost = model.getMovementQty().multiply(costThisLevel).subtract(provisionOfPurchaseCost);
				adjustCostLowerLevel =  model.getMovementQty().multiply(costLowLevel).subtract(provisionOfPurchaseCostLL);
				
				accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
				accumulatedAmount = accumulatedAmount.add(adjustCost);//accumulatedQuantity.signum() > 0 ? accumulatedAmount.add(adjustCost) : accumulatedAmount.add(adjustCost.negate());
				alignAmountWithQuantity();
				
				accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
				accumulatedAmountLowerLevel =  accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(adjustCostLowerLevel) : 
					accumulatedAmountLowerLevel.add(adjustCostLowerLevel.negate());
						
				
				currentCostPrice = accumulatedAmount.divide(
						accumulatedQuantity.signum() != 0 ? accumulatedQuantity
								: BigDecimal.ONE, accountSchema.getCostingPrecision(),
								RoundingMode.HALF_UP);
				currentCostPriceLowerLevel = accumulatedAmountLowerLevel.divide(accumulatedQuantity
						.signum() != 0 ? accumulatedQuantity : BigDecimal.ONE, accountSchema
						.getCostingPrecision(), RoundingMode.HALF_UP);

				return;
			}
			else
			{
                    accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(movementQuantity);
                    currentCostPrice = costThisLevel;
                    currentCostPriceLowerLevel = costLowLevel;
			}
			if (transaction.getMovementType().compareTo(MTransaction.MOVEMENTTYPE_VendorReceipts) == 0
					&& MCostElement.get(transaction.getCtx(), dimension.getM_CostElement_ID()).getCostElementType().compareTo(MCostElement.COSTELEMENTTYPE_LandedCost) == 0){
				isReceiptLandedCost = true;
				adjustCost = Env.ZERO;
				adjustCostLowerLevel = Env.ZERO;
			}
			
            amount = movementQuantity.multiply(costThisLevel);
            amountLowerLevel = movementQuantity.multiply(costLowLevel);

            accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
			accumulatedAmount = accumulatedAmount.add(amount).add(costThisLevel.signum() > 0 ? adjustCost : Env.ZERO);
			
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
			accumulatedAmountLowerLevel = accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(amountLowerLevel) : accumulatedAmountLowerLevel.add(amountLowerLevel.negate());
			if (model instanceof MMovementLine && costDetail!=null){
				costDetail.setAmt(amount);
			}
			if (!isReceiptLandedCost && costThisLevel.signum() > 0 && movementQuantity.signum() > 0)
			{
				BigDecimal chainAmount = getNewAccumulatedAmount(lastCostDetail).add(amount).add(adjustCost);
				boolean significant = chainAmount.abs().compareTo(RESIDUAL_TOLERANCE) > 0;
				boolean valueWithoutStock = accumulatedQuantity.signum() == 0 && significant;
				boolean oppositeSigns = accumulatedQuantity.signum() * chainAmount.signum() < 0 && significant;
				BigDecimal alignedAmount = accumulatedQuantity.multiply(costThisLevel);
				BigDecimal difference = alignedAmount.subtract(chainAmount);
				boolean negativeStockCovered = quantityOnHand.signum() < 0 && difference.abs().compareTo(RESIDUAL_TOLERANCE) > 0;
				if (negativeStockCovered || valueWithoutStock || oppositeSigns)
				{
					adjustCost = adjustCost.add(difference);
					accumulatedAmount = alignedAmount;
				}
			}
		}
		else if (transaction.getMovementType().endsWith("-")) {
			// Use the last current cost price for out transaction			

			currentCostPrice = getNewCurrentCostPrice(lastCostDetail, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP);
			currentCostPriceLowerLevel = getNewCurrentCostPriceLowerLevel(lastCostDetail, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP);
		
			amount = transaction.getMovementQty().multiply(currentCostPrice);
			amountLowerLevel = movementQuantity.multiply(currentCostPriceLowerLevel);

			accumulatedQuantity = getNewAccumulatedQuantity(lastCostDetail).add(
                    movementQuantity);
			
			accumulatedAmount = getNewAccumulatedAmount(lastCostDetail);
			accumulatedAmount = accumulatedAmount.add(amount);
			
			accumulatedAmountLowerLevel = getNewAccumulatedAmountLowerLevel(lastCostDetail);
			accumulatedAmountLowerLevel = accumulatedQuantity.signum() > 0 ? accumulatedAmountLowerLevel.add(amountLowerLevel) : accumulatedAmountLowerLevel.add(amountLowerLevel.negate());
		
			if(costDetail != null)
			{	
				costDetail.setAmt(currentCostPrice.multiply(movementQuantity.abs()));
				costDetail.setAmtLL(currentCostPriceLowerLevel.multiply(movementQuantity).abs());
			}
		}
		
		//create new cost
		if (costDetail == null)
			return;
		
		updateAmountCost();
	}

	private void createCostDetail() {

		// Ignore reversal transaction because is created based on the original
		// transaction
		//Validate if model have a reverses and processing of reverse
		if (model.getReversalLine_ID() > 0 && costDetail == null && !model.isReversalParent()) {
			createReversalCostDetail();
			return;
		} 
		else if (model.getReversalLine_ID() > 0 && !model.isReversalParent())
			return;
		

		int seqNo = lastCostDetail.getSeqNo() + 10;
		// create a new cost detail or is necessary create a new cost detail for
		// adjustment
		/*if ((transaction.getM_Transaction_ID() != lastCostDetail
				.getM_Transaction_ID()
				&& costDetail == null)
				|| (adjustCost.add(adjustCostLowerLevel).signum() != 0
				&& costDetail == null))*/
		if(costDetail == null){
			// set Movement Qty in Zero because is a adjustment
			// if exist adjustment cost for Landed Cost Allocation or Match Inv then set the movement qty to zero
			if ((adjustCost.add(adjustCostLowerLevel).signum() != 0 && costDetail != null)
			|| (model instanceof MLandedCostAllocation || model instanceof MMatchInv))
				movementQuantity = Env.ZERO;

			// create new cost detail
			costDetail = new MCostDetail(transaction, accountSchema.getC_AcctSchema_ID(),
					dimension.getM_CostType_ID(),
					dimension.getM_CostElement_ID(), isReceiptOfLandedCostElement() ? Env.ZERO : currentCostPrice
							.multiply(movementQuantity).abs(),
							isReceiptOfLandedCostElement() ? Env.ZERO : currentCostPriceLowerLevel.multiply(movementQuantity).abs(),
					movementQuantity, transaction.get_TrxName());
			// set account date for this cost detail
			costDetail.setDateAcct(dateAccounting);
			costDetail.setSeqNo(seqNo);

			// set transaction id
			if (transaction != null)
				costDetail.setM_Transaction_ID(transaction.getM_Transaction_ID());
			// set if transaction is sales order type or not
			if (isSalesTransaction != null && isSalesTransaction)
				costDetail.setIsSOTrx(isSalesTransaction);
			else if (isSalesTransaction != null && !isSalesTransaction)
				costDetail.setIsSOTrx(model.isSOTrx());

			if (adjustCost.signum() != 0 || adjustCostLowerLevel.signum() != 0) {
				String description = costDetail.getDescription() != null ? costDetail
						.getDescription() : "";
				// update adjustment cost this level
				if (adjustCost.signum() != 0) {
					costDetail.setCostAdjustmentDate(model.getDateAcct());
					costDetail.setCostAdjustment(adjustCost);
					costDetail.setCostAmt(BigDecimal.ZERO);
					costDetail.setAmt(costDetail.getAmt().add(costDetail.getCostAdjustment()));
					costDetail.setDescription(description + Msg.parseTranslation(Env.getCtx() , "@CostAdjustment@ ") + adjustCost);
				}
				// update adjustment cost lower level
				if (adjustCostLowerLevel.signum() != 0) {
					description = costDetail.getDescription() != null ? costDetail.getDescription() : "";
					costDetail.setCostAdjustmentDateLL(model.getDateAcct());
					costDetail.setCostAdjustmentLL(adjustCostLowerLevel);
					costDetail.setCostAmtLL(BigDecimal.ZERO);
					costDetail.setAmtLL(costDetail.getCostAmtLL().add(costDetail.getCostAdjustmentLL()));
					costDetail.setDescription(description + Msg.parseTranslation(Env.getCtx() , "@CostAdjustmentLL@ ")+ adjustCost);
				}
			}

			updateAmountCost();
			return;
		}
	}

	public MCostDetail process() {		
		calculate();
		createCostDetail();
		alignReversalWithStock();
		updateInventoryValue();
		createCostAdjustment();
		return costDetail;
	}

	public void createCostAdjustment() {
		// only re process cost detail if account schema need adjust cost
		if (!accountSchema.isAdjustCOGS())
			return;
		// void the cycle process, only process the adjustment
		if (costDetail == null || costDetail.isProcessing())
			return;

		// Check if cost detail is an earlier transaction
		// get the cost details that need be re process before this cost
		// transaction
		List<MCostDetail> costDetails = MCostDetail.getAfterDate(costDetail, costingLevel);
		if (costDetails == null || costDetails.size() == 0)
			return;
		
		MCostDetail lastCostDetail = costDetail;
		costDetail = null;
		//Renumber sequence
		for (MCostDetail cost : costDetails) {
			cost.setSeqNo(lastCostDetail.getSeqNo() + 10); // remunerate sequence
			cost.setProcessing(true);
			cost.saveEx();
			lastCostDetail = cost;
			// Only uncomment to debug
			// Trx.get(cd.get_TrxName(), false).commit();
		}
		for (MCostDetail cost : costDetails) {
			adjustCostDetail(cost);
            cost.setProcessing(false);
            cost.saveEx();
 			//clearAccounting(cd);
			// Only uncomment to debug
			// Trx.get(cd.get_TrxName(), false).commit();
		}
	}

	@Override
	protected List<CostComponent> getCalculatedCosts() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Average Invoice Get the New Current Cost Price This Level
	 * @param cost Cost Detail
	 * @param scale Scale
	 * @param roundingMode Rounding Mode
	 * @return New Current Cost Price This Level
	 * @deprecated
	 */
	public BigDecimal getNewCurrentCostPrice(MCostDetail cost, int scale, int roundingMode) {
		return getNewCurrentCostPrice(cost, scale, RoundingMode.valueOf(roundingMode));
	}
	
	/**
	 * Average Invoice Get the New Current Cost Price This Level
	 * @param cost Cost Detail
	 * @param scale Scale
	 * @param roundingMode Rounding Mode
	 * @return New Current Cost Price This Level
	 */
	public BigDecimal getNewCurrentCostPrice(MCostDetail cost, int scale, RoundingMode roundingMode) {
		if (getNewAccumulatedQuantity(cost).signum() != 0 && getNewAccumulatedAmount(cost).signum() != 0) {
			return getNewAccumulatedAmount(cost).divide(getNewAccumulatedQuantity(cost), scale, roundingMode);
		}
		
		return BigDecimal.ZERO;
	}

	/**
	 * Average Invoice Get the New Cumulated Amt This Level
	 * @param cost Cost Detail
	 * @return New Cumulated Amt This Level
	 */
	public BigDecimal getNewAccumulatedAmount(MCostDetail cost) {

		BigDecimal accumulatedAmount = Env.ZERO;
		BigDecimal multiplier = getMultiplierTrx(cost);
		BigDecimal costAmount = cost.getCostAmt().multiply(multiplier);
		BigDecimal costAmountAdj = cost.getCostAdjustment().multiply(multiplier);
		//BigDecimal newCummulatedQty = getNewAccumulatedQuantity(cost);
		accumulatedAmount = cost.getCumulatedAmt().add(costAmount).add(costAmountAdj);
		if (cost.getQty().signum() == 0)
		{
			if (isReceiptLandedCost) {
				costAmountAdj = cost.getCostAdjustment();
				accumulatedAmount = cost.getCumulatedAmt().add(cost.getCostAmt()).add(cost.getCostAdjustment());
			}
		}
		return accumulatedAmount;
	}

	/**
	 * Average Invoice Get the new Cumulated Amt Low Level
	 * @param cost MCostDetail
	 * @return New Cumulated Am Low Level
	 */
	public BigDecimal getNewAccumulatedAmountLowerLevel(MCostDetail cost) {
		
		BigDecimal accumulatedAmountLowerLevel = Env.ZERO;
		BigDecimal multiplier = getMultiplierTrxLL(cost);
		BigDecimal costAmount = cost.getCostAmtLL().multiply(multiplier);
		BigDecimal costAmountAdj = cost.getCostAdjustmentLL().multiply(multiplier);
		//BigDecimal newCummulatedQty = getNewAccumulatedQuantity(cost);
		accumulatedAmountLowerLevel = cost.getCumulatedAmtLL().add(costAmount).add(costAmountAdj);
		return accumulatedAmountLowerLevel;
	}

	/**
	 * Average Invoice Get the New Current Cost Price low level
	 * @param cost Cost Detail
	 * @param scale Scale
	 * @param roundingMode Rounding Mode
	 * @return New Current Cost Price low level
	 * @deprecated
	 */
	public BigDecimal getNewCurrentCostPriceLowerLevel(MCostDetail cost, int scale, int roundingMode) {
		return getNewCurrentCostPriceLowerLevel(cost, scale, RoundingMode.valueOf(roundingMode));
	}

	/**
	 * Average Invoice Get the New Current Cost Price low level
	 * @param cost Cost Detail
	 * @param scale Scale
	 * @param roundingMode Rounding Mode
	 * @return New Current Cost Price low level
	 */
	public BigDecimal getNewCurrentCostPriceLowerLevel(MCostDetail cost, int scale, RoundingMode roundingMode) {
		if (getNewAccumulatedQuantity(cost).signum() != 0 && getNewAccumulatedAmountLowerLevel(cost).signum() != 0) {
			return getNewAccumulatedAmountLowerLevel(cost).divide(getNewAccumulatedQuantity(cost), scale, roundingMode);
		}
		
		return BigDecimal.ZERO;
	}


	/**
	 * Average Invoice Get the new Cumulated Qty
	 * @param cost Cost Detail
	 * @return New Accumulated Quantity
	 */
	public BigDecimal getNewAccumulatedQuantity(MCostDetail cost) {
		    return cost.getCumulatedQty().add(cost.getQty());
	}

	/**
	 * Update Cost Amt
	 */
	public void updateAmountCost() {
		
		if (movementQuantity.signum() > 0) {
			costDetail.setCostAmt(costDetail.getAmt().subtract(costDetail.getCostAdjustment()));
			costDetail.setCostAmtLL(costDetail.getAmtLL().subtract(costDetail.getCostAdjustmentLL()));
		}	
		else if (movementQuantity.signum() < 0 ) {
			costDetail.setCostAmt(costDetail.getAmt().add(adjustCost).subtract(adjustCost.signum() == 0 ? costDetail.getCostAdjustment() : Env.ZERO));
			costDetail.setCostAmtLL(costDetail.getAmtLL().add(adjustCostLowerLevel).subtract(adjustCostLowerLevel.signum() == 0 ? costDetail.getCostAdjustmentLL() : Env.ZERO));
		}

        costDetail.setCumulatedQty(getNewAccumulatedQuantity(lastCostDetail));
        costDetail.setCumulatedAmt(getNewAccumulatedAmount(lastCostDetail));
        costDetail.setCumulatedAmtLL(getNewAccumulatedAmountLowerLevel(lastCostDetail));
        costDetail.setCurrentCostPrice(currentCostPrice);
        costDetail.setCurrentCostPriceLL(currentCostPriceLowerLevel);

		// set the id for model
		final String idColumnName = CostEngine.getIDColumnName(model);
		costDetail.set_ValueOfColumn(idColumnName, CostEngine.getIDColumn(model));
		if (model instanceof MInOutLine)
		{	
			MInOutLine ioLine =  (MInOutLine) model;
			costDetail.setC_OrderLine_ID(ioLine.getC_OrderLine_ID());
			// IMPORTANT : reset possible provision purchase cost processed
			costDetail.setC_InvoiceLine_ID(0);
		}

		if (model instanceof MMatchInv )
		{	
			MMatchInv iMatch =  (MMatchInv) model;
			costDetail.setM_MatchInv_ID(model.get_ID());
			if(costDetail.getM_InOutLine_ID() == 0)
				costDetail.setM_InOutLine_ID(iMatch.getM_InOutLine_ID());

			if(costDetail.getC_InvoiceLine_ID() == 0)
				costDetail.setC_InvoiceLine_ID(iMatch.getM_InOutLine_ID());
		}
		if(model instanceof MMatchPO )
		{
			MMatchPO poMatch =  (MMatchPO) model;
			costDetail.setM_MatchPO_ID(poMatch.getM_MatchPO_ID());
			if (costDetail.getM_InOutLine_ID() == 0)
				costDetail.setM_InOutLine_ID(poMatch.getM_InOutLine_ID());
		}
		if (model instanceof MLandedCostAllocation) {
			MLandedCostAllocation allocation = (MLandedCostAllocation) model;
			costDetail.setM_InOutLine_ID(allocation.getM_InOutLine_ID());
			costDetail.setC_InvoiceLine_ID(allocation.getC_InvoiceLine_ID());
			costDetail.setC_LandedCostAllocation_ID(allocation.getC_LandedCostAllocation_ID());
			costDetail.setProcessed(false);
		}
		costDetail.saveEx();
	}

    public void updateInventoryValue() {
        if (accumulatedQuantity.signum() != 0)
        {
            dimension.setCurrentCostPrice(accumulatedAmount.divide(accumulatedQuantity, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP));
            dimension.setCurrentCostPriceLL(accumulatedAmountLowerLevel.divide(accumulatedQuantity, accountSchema.getCostingPrecision(), RoundingMode.HALF_UP));
        }else {
        	dimension.setCurrentCostPrice(Env.ZERO);
        	dimension.setCurrentCostPriceLL(Env.ZERO);
        }
        	
        if (model.getReversalLine_ID() != 0)
		{
			if (lastCostDetail == null)
				return;
		}
        dimension.setCumulatedAmt(accumulatedAmount);
        dimension.setCumulatedAmtLL(accumulatedAmountLowerLevel);
        dimension.setCumulatedQty(accumulatedQuantity);
        dimension.setCurrentQty(accumulatedQuantity);
        dimension.saveEx();
    }


    /**
	 * Recalculate Cost Detail
	 * @param cost
     */
	public void adjustCostDetail(MCostDetail cost) {

        Properties ctx =  cost.getCtx();
        String trxName = cost.get_TrxName();
        int transactionId = cost.getM_Transaction_ID();
        int clientId = cost.getAD_Client_ID();

		MTransaction transaction = new MTransaction(ctx, transactionId, trxName);

		MCostType costType = (MCostType) cost.getM_CostType();
		MCostElement costElement = (MCostElement) cost.getM_CostElement();
		MAcctSchema accountSchema = (MAcctSchema) cost.getC_AcctSchema();

        CostEngineFactory.getCostEngine(accountSchema.getAD_Client_ID())
                .clearAccounting(accountSchema, transaction);

		if (MTransaction.MOVEMENTTYPE_VendorReceipts.equals(transaction.getMovementType()))
		{
			MInOutLine line = (MInOutLine) transaction.getDocumentLine();
			if (MCostElement.COSTELEMENTTYPE_Material.equals(costElement.getCostElementType()))
			{
                if (cost.getM_InOutLine_ID() > 0 && cost.getQty().signum() !=  0 )
                {
                    CostEngineFactory.getCostEngine(clientId).createCostDetail(accountSchema, costType, costElement, transaction, line, true);
                }
                else if (cost.getM_InOutLine_ID() > 0 && cost.getQty().signum() != 0 && cost.getC_OrderLine_ID() > 0) {
                    List<MMatchPO> orderMatches = MMatchPO.getInOutLine(line);
                    for (MMatchPO match : orderMatches) {
                        if (match.getM_InOutLine_ID() == line.getM_InOutLine_ID()
                                && match.getM_Product_ID() == transaction.getM_Product_ID()) {
                            CostEngineFactory.getCostEngine(clientId)
                                    .createCostDetail(accountSchema, costType, costElement, transaction, match, true);
                        }
                    }
                }
                else if (cost.getM_InOutLine_ID() > 0 && cost.getQty().signum() == 0 && cost.getC_InvoiceLine_ID() > 0 && cost.getC_LandedCostAllocation_ID() ==0) {
                    List<MMatchInv> invoiceMatches = MMatchInv.getInOutLine(line);
                    for (MMatchInv match : invoiceMatches) {
                        if (match.getM_Product_ID() == transaction.getM_Product_ID()) {
                            CostEngineFactory.getCostEngine(clientId).createCostDetail(accountSchema, costType, costElement, transaction, match, true);
                        }
                    }
                }
			}// only own allocation
			if (cost.getC_LandedCostAllocation_ID()!=0)
			{
				MLandedCostAllocation allocation = (MLandedCostAllocation)cost.getC_LandedCostAllocation();
				{
					CostEngineFactory
					.getCostEngine(clientId)
					.createCostDetail(accountSchema, costType, costElement, transaction, allocation, true);
				}
			}
		}
        else
            CostEngineFactory.getCostEngine(clientId).createCostDetail(
                    accountSchema, costType, costElement, transaction, transaction.getDocumentLine(), true);
	}	

	private void alignReversalWithStock() {
		if (costDetail == null || !costDetail.isReversal() || costDetail.getM_Transaction_ID() != transaction.getM_Transaction_ID())
			return;
		BigDecimal multiplier = getMultiplierTrx(costDetail);
		if (multiplier.signum() == 0)
			return;
		BigDecimal quantityAfter = getNewAccumulatedQuantity(costDetail);
		BigDecimal amountAfter = getNewAccumulatedAmount(costDetail);
		boolean significant = amountAfter.abs().compareTo(RESIDUAL_TOLERANCE) > 0;
		boolean valueWithoutStock = quantityAfter.signum() == 0 && significant;
		boolean oppositeSigns = quantityAfter.signum() * amountAfter.signum() < 0 && significant;
		if (!valueWithoutStock && !oppositeSigns)
			return;
		BigDecimal alignedAmount = quantityAfter.multiply(costDetail.getCurrentCostPrice().abs());
		BigDecimal adjustment = alignedAmount.subtract(amountAfter).multiply(multiplier);
		costDetail.setCostAdjustment(costDetail.getCostAdjustment().add(adjustment));
		costDetail.setAmt(costDetail.getAmt().add(adjustment));
		costDetail.saveEx();
		accumulatedQuantity = quantityAfter;
		accumulatedAmount = alignedAmount;
	}

	private void alignAmountWithQuantity() {
		boolean significant = accumulatedAmount.abs().compareTo(RESIDUAL_TOLERANCE) > 0;
		boolean valueWithoutStock = accumulatedQuantity.signum() == 0 && significant;
		boolean oppositeSigns = accumulatedQuantity.signum() * accumulatedAmount.signum() < 0 && significant;
		if (valueWithoutStock || oppositeSigns)
		{
			BigDecimal alignedAmount = accumulatedQuantity.multiply(costThisLevel);
			adjustCost = adjustCost.add(alignedAmount.subtract(accumulatedAmount));
			accumulatedAmount = alignedAmount;
		}
	}

	private int getReceiptAttributeSetInstanceId() {
		int attributeSetInstanceId = model.getM_AttributeSetInstance_ID();
		if (attributeSetInstanceId == 0 && model instanceof MMatchInv)
			attributeSetInstanceId = ((MMatchInv) model).getM_InOutLine().getM_AttributeSetInstance_ID();
		return attributeSetInstanceId;
	}

	protected BigDecimal roundCost(BigDecimal price, int accountSchemaId) {
		// Fix Cost Precision
		int precision = MAcctSchema.get(Env.getCtx(), accountSchemaId)
				.getCostingPrecision();
		BigDecimal priceRounded = price;
		if (priceRounded.scale() > precision) {
			priceRounded = priceRounded.setScale(precision,
					RoundingMode.HALF_UP);
		}
		return priceRounded;
	}
	
	private BigDecimal getMultiplierTrx(MCostDetail cost) {
		AtomicReference<BigDecimal> result = new AtomicReference<BigDecimal>(Env.ONE);
		
		Optional.ofNullable(cost).ifPresent(costDetail ->{
			if (costDetail.getC_InvoiceLine_ID() == 0
					&& costDetail.getC_LandedCostAllocation_ID() == 0) 
				result.set(new BigDecimal(costDetail.getQty().multiply(costDetail.getCurrentCostPrice()).signum()));			
		});
		return result.get();
	}
	
	private BigDecimal getMultiplierTrxLL(MCostDetail cost) {
		AtomicReference<BigDecimal> result = new AtomicReference<BigDecimal>(Env.ONE);
		
		Optional.ofNullable(cost).ifPresent(costDetail ->{
			if (costDetail.getC_InvoiceLine_ID() == 0
					&& costDetail.getC_LandedCostAllocation_ID() == 0) 
				result.set(new BigDecimal(costDetail.getQty().multiply(costDetail.getCurrentCostPriceLL()).signum()));			
		});
		return result.get();
	}
}
