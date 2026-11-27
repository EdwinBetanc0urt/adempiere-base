/**
 * Copyright (C) 2003-2017, e-Evolution Consultants S.A. , http://www.e-evolution.com
 * This program is free software, you can redistribute it and/or modify it
 * under the terms version 2 of the GNU General Public License as published
 * or (at your option) any later version.
 * by the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along
 * with this program, if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 * For the text or an alternative of this public license, you may reach us
 * or via info@adempiere.net or http://www.adempiere.net/license.html
 * Email: victor.perez@e-evolution.com, http://www.e-evolution.com , http://github.com/e-Evolution
 * Created by victor.perez@e-evolution.com , www.e-evolution.com
 */

package org.eevolution.distribution.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.core.domains.models.X_DD_FreightLine;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MInOut;
import org.compiere.model.MOrder;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPackage;
import org.compiere.model.MWarehouse;

/**
 * Domain Model for Freight Line Order
 * @author victor.perez@e-evolution.com, http://www.e-evolution.com , http://github.com/e-Evolution
 */
public class MDDFreightLine extends X_DD_FreightLine {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2953035352565001669L;

	/**
     * Constructor  Freight Line Order
     * @param ctx
     * @param freightlineId
     * @param trxName
     */
    public MDDFreightLine(Properties ctx, int freightlineId, String trxName) {
        super(ctx, freightlineId, trxName);
    }

    /**
     * Constructor  Freight Line Order
     * @param ctx
     * @param rs
     * @param trxName
     */
    public MDDFreightLine(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }
    
	/**	Parent	*/
	private MDDFreight parent = null;
	
	/**
	 * 	Get Parent
	 *	@return parent
	 */
	public MDDFreight getParent() {
		if (parent == null) {
			parent = new MDDFreight(getCtx(), getDD_Freight_ID(), get_TrxName());
		}
		return parent;
	}	//	getParent

	/**	Order reference of the line (column added by migration 1.4.7/10450, not generated in the model)	*/
	public static final String COLUMNNAME_C_Order_ID = "C_Order_ID";

	/**
	 * Fill the ship from / ship to locations, the order and the business partner from a package.
	 * Only empty values are filled, so the values already set by the caller are kept.
	 * <ul>
	 * 	<li>Order: package order (M_Package.C_Order_ID), or the order of the package shipment</li>
	 * 	<li>Ship From: location of the package warehouse (order / shipment warehouse as fallback), or the organization location</li>
	 * 	<li>Ship To: location of the package business partner location (order / shipment as fallback)</li>
	 * </ul>
	 * @param sourcePackage package of the freight line
	 */
	public void setFromPackage(MPackage sourcePackage) {
		if (sourcePackage == null || sourcePackage.getM_Package_ID() <= 0) {
			return;
		}
		MInOut shipment = null;
		if (sourcePackage.getM_InOut_ID() > 0) {
			shipment = new MInOut(getCtx(), sourcePackage.getM_InOut_ID(), get_TrxName());
		}
		//	Order
		int orderId = 0;
		if (sourcePackage.get_ColumnIndex(COLUMNNAME_C_Order_ID) >= 0) {
			orderId = sourcePackage.get_ValueAsInt(COLUMNNAME_C_Order_ID);
		}
		if (orderId <= 0 && shipment != null) {
			orderId = shipment.getC_Order_ID();
		}
		MOrder order = null;
		if (orderId > 0) {
			order = new MOrder(getCtx(), orderId, get_TrxName());
			if (get_ColumnIndex(COLUMNNAME_C_Order_ID) >= 0
					&& get_ValueAsInt(COLUMNNAME_C_Order_ID) <= 0) {
				set_ValueOfColumn(COLUMNNAME_C_Order_ID, orderId);
			}
		}
		//	Business Partner
		if (getC_BPartner_ID() <= 0) {
			if (sourcePackage.getC_BPartner_ID() > 0) {
				setC_BPartner_ID(sourcePackage.getC_BPartner_ID());
			} else if (order != null && order.getC_BPartner_ID() > 0) {
				setC_BPartner_ID(order.getC_BPartner_ID());
			} else if (shipment != null && shipment.getC_BPartner_ID() > 0) {
				setC_BPartner_ID(shipment.getC_BPartner_ID());
			}
		}
		//	Ship From
		if (getC_LocFrom_ID() <= 0) {
			int warehouseId = sourcePackage.getM_Warehouse_ID();
			if (warehouseId <= 0 && order != null) {
				warehouseId = order.getM_Warehouse_ID();
			}
			if (warehouseId <= 0 && shipment != null) {
				warehouseId = shipment.getM_Warehouse_ID();
			}
			int locationFromId = 0;
			if (warehouseId > 0) {
				locationFromId = MWarehouse.get(getCtx(), warehouseId, get_TrxName()).getC_Location_ID();
			}
			if (locationFromId <= 0) {
				MOrgInfo organizationInfo = MOrgInfo.get(getCtx(), sourcePackage.getAD_Org_ID(), get_TrxName());
				if (organizationInfo != null) {
					locationFromId = organizationInfo.getC_Location_ID();
				}
			}
			if (locationFromId > 0) {
				setC_LocFrom_ID(locationFromId);
			}
		}
		//	Ship To
		if (getC_LocTo_ID() <= 0) {
			int businessPartnerLocationId = sourcePackage.getC_BPartner_Location_ID();
			if (businessPartnerLocationId <= 0 && order != null) {
				businessPartnerLocationId = order.getC_BPartner_Location_ID();
			}
			if (businessPartnerLocationId <= 0 && shipment != null) {
				businessPartnerLocationId = shipment.getC_BPartner_Location_ID();
			}
			if (businessPartnerLocationId > 0) {
				MBPartnerLocation businessPartnerLocation = new MBPartnerLocation(getCtx(), businessPartnerLocationId, get_TrxName());
				if (businessPartnerLocation.getC_Location_ID() > 0) {
					setC_LocTo_ID(businessPartnerLocation.getC_Location_ID());
				}
			}
		}
	}

	@Override
	public String toString() {
		return "MDDFreightLine [getC_LocFrom_ID()=" + getC_LocFrom_ID() + ", getC_LocTo_ID()=" + getC_LocTo_ID()
				+ ", getDD_Freight_ID()=" + getDD_Freight_ID() + ", isInvoiced()=" + isInvoiced() + ", getLine()="
				+ getLine() + ", getShipDate()=" + getShipDate() + ", getVolume()=" + getVolume() + ", getWeight()="
				+ getWeight() + "]";
	}
}
