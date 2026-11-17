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

package org.adempiere.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MProduct;
import org.compiere.model.PO;
import org.compiere.util.Env;
import org.compiere.util.Util;

/**
 * Manual material allocation (tables *MA).
 * When a document line has IsManualAllocation = Y the material policy (FIFO/LIFO) does not
 * delete nor recreate its allocations: the ones entered by the user (UI, mobile or import)
 * are validated and used as they are.
 * The allocations of a manual line always have the same sign as the quantity of the line.
 */
public class ManualAllocation {

	/** Column name IsManualAllocation (document line) */
	public static final String COLUMNNAME_IsManualAllocation = "IsManualAllocation";
	/** Column name M_AttributeSetInstance_ID (allocation) */
	private static final String COLUMNNAME_M_AttributeSetInstance_ID = "M_AttributeSetInstance_ID";
	/** Column name MovementQty (allocation) */
	private static final String COLUMNNAME_MovementQty = "MovementQty";

	/**
	 * Verify if the allocation of a document line is manual
	 * @param line document line
	 * @return true if the line has IsManualAllocation = Y
	 */
	public static boolean isManualAllocation(PO line) {
		if (line == null || line.get_ColumnIndex(COLUMNNAME_IsManualAllocation) < 0) {
			return false;
		}
		return line.get_ValueAsBoolean(COLUMNNAME_IsManualAllocation);
	}

	/**
	 * Set the manual allocation flag, if the line has the column
	 * @param line document line
	 * @param isManualAllocation flag
	 */
	public static void setManualAllocation(PO line, boolean isManualAllocation) {
		if (line == null || line.get_ColumnIndex(COLUMNNAME_IsManualAllocation) < 0) {
			return;
		}
		line.set_ValueOfColumn(COLUMNNAME_IsManualAllocation, isManualAllocation);
	}

	/**
	 * Validate the manual allocations of a line before the stock is changed
	 * @param line document line
	 * @param productId product of the line
	 * @param attributeSetInstanceId attribute set instance of the line (must be 0)
	 * @param quantity signed quantity of the line (same sign as the allocations)
	 * @param allocations allocations of the line
	 */
	public static void validate(PO line, int productId, int attributeSetInstanceId, BigDecimal quantity, List<? extends PO> allocations) {
		String lineInfo = getLineInfo(line);
		BigDecimal expectedQuantity = Optional.ofNullable(quantity).orElse(Env.ZERO);
		if (attributeSetInstanceId != 0) {
			throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @M_AttributeSetInstance_ID@ <> 0");
		}
		if (expectedQuantity.signum() != 0 && (allocations == null || allocations.isEmpty())) {
			throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @NoLines@ (@MovementQty@ = " + expectedQuantity + ")");
		}
		MProduct product = MProduct.get(line.getCtx(), productId);
		BigDecimal totalQuantity = Env.ZERO;
		if (allocations != null) {
			for (PO allocation : allocations) {
				int allocationInstanceId = allocation.get_ValueAsInt(COLUMNNAME_M_AttributeSetInstance_ID);
				BigDecimal allocationQuantity = Optional.ofNullable((BigDecimal) allocation.get_Value(COLUMNNAME_MovementQty)).orElse(Env.ZERO);
				if (allocationInstanceId <= 0) {
					throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @FillMandatory@ @M_AttributeSetInstance_ID@");
				}
				if (allocationQuantity.signum() != expectedQuantity.signum()) {
					throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @MovementQty@ " + allocationQuantity + " / " + expectedQuantity);
				}
				MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(line.getCtx(), allocationInstanceId, line.get_TrxName());
				if (product != null && attributeSetInstance.getM_AttributeSet_ID() != product.getM_AttributeSet_ID()) {
					throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @M_AttributeSet_ID@ <> @M_Product_ID@: " + attributeSetInstance.getDescription());
				}
				if (!Util.isEmpty(attributeSetInstance.getSerNo(), true) && allocationQuantity.abs().compareTo(Env.ONE) != 0) {
					throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @SerNo@ " + attributeSetInstance.getSerNo() + ": @MovementQty@ " + allocationQuantity + " <> 1");
				}
				totalQuantity = totalQuantity.add(allocationQuantity);
			}
		}
		if (totalQuantity.compareTo(expectedQuantity) != 0) {
			throw new AdempiereException(lineInfo + "@IsManualAllocation@ - @MovementQty@ = " + totalQuantity + " <> " + expectedQuantity);
		}
	}

	/**
	 * Get line reference for messages
	 * @param line document line
	 * @return text
	 */
	private static String getLineInfo(PO line) {
		if (line.get_ColumnIndex("Line") >= 0) {
			return "@Line@ " + line.get_ValueAsInt("Line") + ": ";
		}
		return "";
	}
}
