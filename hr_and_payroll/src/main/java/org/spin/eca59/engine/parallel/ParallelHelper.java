/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 or later of the                                  *
 * GNU General Public License as published                                    *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2019 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpya.com                                  *
 *****************************************************************************/
package org.spin.eca59.engine.parallel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.compiere.model.MCommission;
import org.compiere.model.MConversionRate;
import org.compiere.model.MCurrency;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.eevolution.hr.model.MHRAttribute;
import org.eevolution.hr.model.MHRConcept;
import org.eevolution.hr.model.MHRConceptCategory;
import org.eevolution.hr.model.MHRConceptType;
import org.eevolution.hr.model.MHRContract;
import org.eevolution.hr.model.MHRMovement;
import org.eevolution.hr.model.MHRPayroll;
import org.spin.eca59.concept.PayrollConcept;
import org.spin.eca59.employee.PayrollEmployee;
import org.spin.eca59.engine.EngineHelper;
import org.spin.eca59.payroll_process.PayrollProcess;
import org.spin.eca59.rule.RuleContext;
import org.spin.hr.util.TNAUtil;

/**
 * 	Helper class for parallel processig
 *	@author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class 	ParallelHelper implements EngineHelper {
	
	public ParallelHelper(RuleContext ruleContext) {
		this.ruleContext = ruleContext;
		this.engine = (ParallelEngine) ruleContext.getEngine();
		this.engineProcess = ruleContext.getCurrentProcess();
		this.engineEmployee = ruleContext.getCurrentEmployee();
		this.engineConcept = ruleContext.getCurrentConcept();
		this.transactionName = ruleContext.getTransactionName();
	}
	
	private RuleContext ruleContext;
	private ParallelEngine engine;
	private PayrollProcess engineProcess;
	private PayrollEmployee engineEmployee;
	private PayrollConcept engineConcept;
	private String transactionName;
	private Map<String, MHRMovement> lastConceptMap = new HashMap<String, MHRMovement>();
	private Map<String, BigDecimal> conceptAgregateMap = new HashMap<String, BigDecimal>();
	private Map<String, MHRAttribute> attributeInstanceMap = new HashMap<String, MHRAttribute>();
	
	/**	Static Logger	*/
	private static CLogger logger = CLogger.getCLogger (ParallelHelper.class);
	
	public String getTransactionName() {
		return transactionName;
	}
	
	public PayrollConcept getCurrentConcept() {
		return engineConcept;
	}
	
	/**
	 * Get Monthly Salary
	 * @return
	 */
	public double getMonthlySalary() {
		BigDecimal monthtlySalary = engineEmployee.getEmployee().getMonthlySalary();
		if(monthtlySalary != null
				&& !monthtlySalary.equals(Env.ZERO)) {
			return monthtlySalary.doubleValue();
		}
		//	if not exists
		if(engineEmployee.getPayrollId() != 0) {
			MHRPayroll payroll = MHRPayroll.getById(ruleContext.getContext(), engineEmployee.getPayrollId(), null);
			MHRContract contract = MHRContract.getById(ruleContext.getContext(), payroll.getHR_Contract_ID(), null);
			if(contract != null
					&& contract.getMonthlySalary_ID() != 0) {
				MHRConcept concept = MHRConcept.getById(ruleContext.getContext(), contract.getMonthlySalary_ID() , null);
				//	Get from attribute
				return getAttribute(concept.getValue());
			}
		}
		//	Default
		return 0;
	}
	
	/**
	 * Get Daily Salary
	 * @return
	 */
	public double getDailySalary() {
		BigDecimal dailySalary = engineEmployee.getEmployee().getDailySalary();
		if(dailySalary != null
				&& !dailySalary.equals(Env.ZERO)) {
			return dailySalary.doubleValue();
		}
		//	if not exists
		if(engineEmployee.getPayrollId() != 0) {
			MHRPayroll payroll = MHRPayroll.getById(ruleContext.getContext(), engineEmployee.getPayrollId(), null);
			MHRContract contract = MHRContract.getById(ruleContext.getContext(), payroll.getHR_Contract_ID(), null);
			if(contract != null
					&& contract.getDailySalary_ID() != 0) {
				MHRConcept concept = MHRConcept.getById(ruleContext.getContext(), contract.getDailySalary_ID(), null);
				//	Get from attribute
				return getAttribute(concept.getValue());
			}
		}
		//	Default
		return 0;
	}

	// Helper methods -------------------------------------------------------------------------------

	/**
	 * Helper Method : get the value of the concept
	 * @param conceptValue
	 * @return
	 */
	public double getConcept (String conceptValue)
	{
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue.trim(), null);
		if (concept == null)
			return 0;
		return getValueFromMovement(engine.createMovementFromConcept(engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName), concept.getColumnType()).doubleValue();
	} // getConcept

	/**
	 * Helper Method : get the value of the concept string type
	 * @param pconcept
	 * @return String value of concept
	 */
	public String getConceptString (String pconcept)
	{
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), pconcept.trim(), null);
		if (concept == null)
			return null;
		
		MHRMovement movement = engine.createMovementFromConcept(engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName);
		if (movement != null) {
			return movement.getTextMsg();
		}
		return null;
	} // getConceptString

	/**
	 * Helper Method : get the value of the concept date type
	 * @param conceptValue
	 * @return Timestamp value of concept
	 */
	public Timestamp getConceptDate (String conceptValue)
	{
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue.trim(), null);
		if (concept == null)
			return null;

		MHRMovement movement = engine.createMovementFromConcept(engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName);
		if (movement != null) {
			if (MHRConcept.COLUMNTYPE_Date.equals(concept.getColumnType())) {
				return movement.getServiceDate();
			}
		}
			return null;
	} // getConceptDate

	public Object getConceptValue(String conceptValue) {
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue.trim(), null);
		if (concept == null)
			return null;
		MHRMovement movement = engine.createMovementFromConcept(
				engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName);
		if (movement == null)
			return null;
		String columnType = concept.getColumnType();
		if (MHRConcept.COLUMNTYPE_Text.equals(columnType))
			return movement.getTextMsg();
		if (MHRConcept.COLUMNTYPE_Date.equals(columnType))
			return movement.getServiceDate();
		// Amount or Quantity → BigDecimal
		return getValueFromMovement(movement, columnType);
	}

	/**
	 * Helper Method : get the sum of the concept values, grouped by the concept type
	 * @param typeValue
	 * @return
	 */
	public double getConceptType (String typeValue)
	{
		final MHRConceptType conceptType = MHRConceptType.getByValue(ruleContext.getContext(), typeValue, null);
		if (conceptType == null)
			return 0.0;
		AtomicReference<BigDecimal> value = new AtomicReference<>(Env.ZERO);
		engineProcess.getConcepts().forEach(conceptId -> {
			MHRConcept concept = MHRConcept.getById(ruleContext.getContext(), conceptId , null);
			if(concept.getHR_Concept_Type_ID() == conceptType.get_ID()) {
				Optional.ofNullable(engine.createMovementFromConcept(engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName))
					.ifPresent(
							movement -> value.getAndUpdate(
							oldValue -> oldValue.add(
									getValueFromMovement(movement, concept.getColumnType()
											)
									)
							)
							);
			}
		});
		return value.get().doubleValue();
	} // Get Concept Type

	private BigDecimal getValueFromMovement(MHRMovement movement, String columnType) {
		if (movement != null) {
			if(MHRConcept.COLUMNTYPE_Amount.equals(columnType)) {
				return movement.getAmount();
			} else if (MHRConcept.COLUMNTYPE_Quantity.equals(columnType)) {
				return movement.getQty();
			}
		}
		return Env.ZERO;
	}
	

	/**
	 * Helper Method : get the sum of the concept values, grouped by the Category
	 * @param categoryValue
	 * @return
	 * @Deprecated
	 */
	public double getConceptGroup (String categoryValue)
	{
		return getConceptCategory(categoryValue);
	} // getConceptGroup

	/**
	 * Helper Method : get the sum of the concept values, grouped by the Category
	 * @param categoryValue
	 * @return
	 */
	public double getConceptCategory (String categoryValue)
	{
		final MHRConceptCategory conceptCategory = MHRConceptCategory.getByValue(ruleContext.getContext(), categoryValue, null);
		if (conceptCategory == null)
			return 0.0;
		AtomicReference<BigDecimal> value = new AtomicReference<>(Env.ZERO);
		engineProcess.getConcepts().forEach(conceptId -> {
			MHRConcept concept = MHRConcept.getById(ruleContext.getContext(), conceptId , null);
			if(concept.getHR_Concept_Category_ID() == conceptCategory.get_ID()) {
				Optional.ofNullable(engine.createMovementFromConcept(engineProcess, engineEmployee, PayrollConcept.newInstance(concept), transactionName))
				.ifPresent(
						movement -> value.getAndUpdate(
						oldValue -> oldValue.add(
								getValueFromMovement(movement, concept.getColumnType()
										)
								)
						)
						);
			}
		});
		return value.get().doubleValue();
	} // getConceptCategory

	/**
	 * Helper Method : Get Concept [get concept to search key ]
	 * @param listSearchKey Value List
	 * @param amount Amount to search
	 * @param columnParam Number of column to return (1.......8)
	 * @return The amount corresponding to the designated column 'column'
	 */
	public double getList (String listSearchKey, double amount, String columnParam)
	{
		return getList (listSearchKey, engineProcess.getValidFrom(), amount, columnParam , engineConcept.getColumnType());
	}

	/**
	 * Helper Method : Get Concept [get concept to search key ]
	 * @param listSearchKey
	 * @param from
	 * @param amount
	 * @param columnParam
     * @return The amount corresponding to the designated column 'column'
     */
	public double getList (String listSearchKey, Timestamp from , double amount, String columnParam)
	{
		return getList (listSearchKey, from , amount, columnParam , null);
	}

	/**
	 * Helper Method : Get Concept [get concept to search key ]
	 * @param listSearchKey Value List
	 * @from from date to valid list
	 * @param amount Amount to search
	 * @param columnParam Number of column to return (1.......8)
	 * @param columnType can be Amount or null
	 * @return The amount corresponding to the designated column 'column'
	 */
	public double getList (String listSearchKey,Timestamp from, double amount, String columnParam , String columnType)
	{
		BigDecimal value = Env.ZERO;
		String column = columnParam;
		if (MHRConcept.COLUMNTYPE_Amount.equals(columnType) || columnType == null )
		{
			column = column.toString().length() == 1 ? "Col_"+column : "Amount"+column;
			ArrayList<Object> params = new ArrayList<Object>();
			String sqlList = "SELECT " +column+
				" FROM HR_List l " +
				"INNER JOIN HR_ListVersion lv ON (lv.HR_List_ID=l.HR_List_ID) " +
				"INNER JOIN HR_ListLine ll ON (ll.HR_ListVersion_ID=lv.HR_ListVersion_ID) " +
				"WHERE l.IsActive='Y' AND lv.IsActive='Y' AND ll.IsActive='Y' AND l.Value = ? AND " +
				"l.AD_Client_ID in (?,0) AND " +
				"(? BETWEEN lv.ValidFrom AND lv.ValidTo ) AND " +
				"(? BETWEEN ll.MinValue AND	ll.MaxValue)" +
				" ORDER BY l.AD_CLIENT_ID desc ";
			params.add(listSearchKey);
			params.add(engineProcess.getClientId());
			params.add(from);
			params.add(BigDecimal.valueOf(amount));

			value = DB.getSQLValueBDEx(null,sqlList,params);
		}
		//
		if (value == null)
		{
			throw new IllegalStateException("getList Out of Range");
		}
		return value.doubleValue();
	} // getList
	
	public MHRAttribute getAttributeInstance(String conceptValue, Timestamp breakDate) {
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue, null);
		if (concept == null)
			return null;
		return getAttributeInstance(concept, breakDate);
	}
	
	public MHRAttribute getAttributeInstance(String conceptValue) {
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue, null);
		if (concept == null)
			return null;
		return getAttributeInstance(concept, null);
	}
	
	/**
	 * Get attribute like MHRAttribute
	 * @param concept
	 * @param engineEmployee.getBusinessPartnerId()
	 * @param breakDate
	 * @return
	 */
	public MHRAttribute getAttributeInstance(MHRConcept concept, Timestamp breakDate) {
		if (concept == null)
			return null;
		//	validate break date
		if(breakDate == null)
			breakDate = engineProcess.getValidFrom();
		//	BPartner
		String key = concept.getValue() + "|" + engineEmployee.getBusinessPartnerId() + "|" + breakDate.getTime();
		//	Get from cache
		MHRAttribute attribute = attributeInstanceMap.get(key);
		if(attribute != null) {
			return attribute;
		}
		//	
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(breakDate);
		//check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(engineProcess.getClientId());
		//	Add criteria by payroll
		if(engineProcess.getPayrollId() > 0) {
			whereClause.append(" AND (HR_Payroll_ID=? OR HR_Payroll_ID IS NULL)");
			params.add(engineProcess.getPayrollId());
		}
		//	by department
		if(engineEmployee.getDepartmentId() > 0) {
			whereClause.append(" AND (HR_Department_ID=? OR HR_Department_ID IS NULL)");
			params.add(engineEmployee.getDepartmentId());
		}
		//	by job
		if(engineEmployee.getJobId() > 0) {
			whereClause.append(" AND (HR_Job_ID=? OR HR_Job_ID IS NULL)");
			params.add(engineEmployee.getJobId());
		}
		//check concept
		whereClause.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID = HR_Attribute.HR_Concept_ID" 
				+ " AND c.Value = ?)");
		params.add(concept.getValue());
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID + " = ?");
			params.add(engineEmployee.getBusinessPartnerId());
		}
		//	Get from query
		attribute = new Query(ruleContext.getContext(), MHRAttribute.Table_Name, whereClause.toString(), null)
			.setParameters(params)
			.setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
			.setOnlyActiveRecords(true)
			.first();
		//	Set
		if(attribute != null) {
			attributeInstanceMap.put(key, attribute);
		}
		//	Return
		return attribute;
	}
	
	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * @param conceptValue - Value to Concept
	 * @return	Amount of concept, applying to engineEmployee.getEmployee()
	 */ 
	public double getAttribute (String conceptValue) {
		return getAttribute(conceptValue, null);
	} // 
	
	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and break date]
	 * @param conceptValue
	 * @param breakDate
	 * @return
	 */
	public double getAttribute (String conceptValue, Timestamp breakDate) {
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue, null);
		if (concept == null)
			return 0.0;
		//	Get from PO
		MHRAttribute attribute = getAttributeInstance(concept, breakDate);
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount)) {
			BigDecimal rate = Env.ONE;
			BigDecimal amount = attribute.getAmount();
			if(attribute.isConvertedAmount() && attribute.getC_Currency_ID() != engineProcess.getCurrencyId()) {
				int precision = MCurrency.getStdPrecision(ruleContext.getContext(), Env.getContextAsInt(engineProcess.getContext(), "#C_Currency_ID"));
				if(concept.getStdPrecision() > 0) {
					precision = concept.getStdPrecision();
				}
				rate = MConversionRate.getRate(attribute.getC_Currency_ID(), engineProcess.getCurrencyId(), engineProcess.getDateAcct(), engineProcess.getConversionTypeId(), engineProcess.getClientId(), engineProcess.getOrganizationId());
				if(rate != null) {
					amount = rate.multiply(amount)
							.setScale(precision, RoundingMode.HALF_UP);
					
				}
			}
			if(amount == null) {
				return 0.0;
			}
			return Optional.ofNullable(amount).orElse(Env.ZERO).doubleValue();
		}

		//something else
		return 0.0; //TODO throw exception ?? 
	} // 


	/**
	 * 	Helper Method : Get Attribute [get Attribute to search key concept ]
	 *  @param conceptValue
	 *  @return ServiceDate
	 */ 
	public Timestamp getAttributeDate (String conceptValue) {
		return getAttributeDate(conceptValue, null);
	} // getAttributeDate
	
	/**
	 * 	Helper Method : Get Attribute [get Attribute to search key concept and break date]
	 *  @param conceptValue
	 *  @return ServiceDate
	 */ 
	public Timestamp getAttributeDate (String conceptValue, Timestamp breakDate) {
		//	Get from PO
		MHRAttribute attribute = getAttributeInstance(conceptValue, breakDate);
		if (attribute == null)
			return null;
		//	
		return attribute.getServiceDate();
	} // getAttributeDate

	/**
	 * 	Helper Method : Get Attribute [get Attribute to search key concept ]
	 *  @param conceptValue
	 *  @return TextMsg
	 */ 
	public String getAttributeString (String conceptValue) {
		return getAttributeString(conceptValue, null);
	} // getAttributeString

	/**
	 * 	Helper Method : Get Attribute [get Attribute to search key concept and break date]
	 *  @param conceptValue
	 *  @return TextMsg
	 */ 
	public String getAttributeString (String conceptValue, Timestamp breakDate) {
		MHRAttribute attribute = getAttributeInstance(conceptValue, breakDate);
		if (attribute == null)
			return null;
		//	
		return attribute.getTextMsg();
	} // getAttributeString
	
	/**
	 * 	Helper Method : Get the number of days between start and end, in Timestamp format
	 *  @param date1 
	 *  @param date2
	 *  @return no. of days
	 */ 
	public int getDays (Timestamp date1, Timestamp date2)
	{		
		// adds one for the last day
		return org.compiere.util.TimeUtil.getDaysBetween(date1,date2) + 1;
	} // getDays


	/**
	 * 	Helper Method : Get the number of days between start and end, in String format
	 *  @param date1 
	 *  @param date2
	 *  @return no. of days
	 */  
	public  int getDays (String date1, String date2)
	{		
		Timestamp dat1 = Timestamp.valueOf(date1);
		Timestamp dat2 = Timestamp.valueOf(date2);
		return getDays(dat1, dat2);
	}  // getDays

	/**
	 * 	Helper Method : Get Months, Date in Format Timestamp
	 *  @param startParam
	 *  @param endParam
	 *  @return no. of month between two dates
	 */ 
	public int getMonths(Timestamp startParam,Timestamp endParam)
	{
		boolean negative = false;
		Timestamp start = startParam;
		Timestamp end = endParam;
		if (end.before(start))
		{
			negative = true;
			Timestamp temp = start;
			start = end;
			end = temp;
		}

		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(start);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		GregorianCalendar calEnd = new GregorianCalendar();

		calEnd.setTime(end);
		calEnd.set(Calendar.HOUR_OF_DAY, 0);
		calEnd.set(Calendar.MINUTE, 0);
		calEnd.set(Calendar.SECOND, 0);
		calEnd.set(Calendar.MILLISECOND, 0);

		if (cal.get(Calendar.YEAR) == calEnd.get(Calendar.YEAR))
		{
			if (negative)
				return (calEnd.get(Calendar.MONTH) - cal.get(Calendar.MONTH)) * -1;
			return calEnd.get(Calendar.MONTH) - cal.get(Calendar.MONTH);
		}

		//	not very efficient, but correct
		int counter = 0;
		while (calEnd.after(cal))
		{
			cal.add (Calendar.MONTH, 1);
			counter++;
		}
		if (negative)
			return counter * -1;
		return counter;
	} // getMonths

	/**
	 * Helper Method : Concept for a period.
	 * Periods with values of 0 -1 1, etc. actual previous one period, next period
	 * 0 corresponds to actual period.
	 * @param conceptValue concept key(value)
	 * @param period search is done by the period value, it helps to search from previous years
	 */
	public double getConcept (String conceptValue, int period) {
		return getConcept(conceptValue, null, period,period, true);
	} // getConcept


	/**
	 * Helper Method : Concept for a range from-to in periods.
	 * Periods with values of 0 -1 1, etc. actual previous one period, next period
	 * 0 corresponds to actual period.
	 * @param conceptValue concept key(value)
	 * @param periodFrom the search is done by the period value, it helps to search from previous years
	 * @param periodTo
	 */
	public double getConcept (String conceptValue, int periodFrom, int periodTo) {
		return getConcept(conceptValue, null, periodFrom,periodTo, true);
	} // getConcept

	/**
	 *  Helper Method : Concept by range from-to in periods from a different payroll
	 *  periods with values 0 -1 1, etc. actual previous one period, next period
	 *  0 corresponds to actual period
	 * @param conceptValue
	 * @param payrollValue is the value of the payroll.
	 * @param periodFrom
	 * @param periodTo the search is done by the period value, it helps to search from previous years
	 */
	public double getConcept(String conceptValue, String payrollValue, int periodFrom, int periodTo) {
		return getConcept(conceptValue, payrollValue, periodFrom , periodTo , true);
	}

	/**
	 *  Helper Method : Concept by range from-to in periods from a different payroll
	 *  periods with values 0 -1 1, etc. actual previous one period, next period
	 *  0 corresponds to actual period
	 * @param conceptValue
	 *  @param payrollValue is the value of the payroll.
	 * @param periodFrom
	 * @param periodTo the search is done by the period value, it helps to search from previous years
	 * @param includeInProcess
	 */
	public double getConcept(String conceptValue, String payrollValue, int periodFrom, int periodTo, boolean includeInProcess) {
		int payrollId;
		if (payrollValue == null) {
			payrollId = engineProcess.getPayrollId();
		} else {
			MHRPayroll payroll = MHRPayroll.getByValue(ruleContext.getContext(), payrollValue, null);
			if(payroll == null)
				return 0.0;
			//	
			payrollId = payroll.get_ID();
		}
		String key = "SUM|" + engineEmployee.getBusinessPartnerId() + "|" + conceptValue + "|" + payrollId + "|" + periodFrom + "|" + periodTo + "|" + includeInProcess;
		if(conceptAgregateMap.containsKey(key)) {
			return Optional.ofNullable(conceptAgregateMap.get(key)).orElse(Env.ZERO).doubleValue();
		}
		BigDecimal amount = new BigDecimal(MHRMovement.getConceptSum(ruleContext.getContext(), conceptValue, payrollId, engineEmployee.getBusinessPartnerId(), engineProcess.getPeriodId(), periodFrom, periodTo, includeInProcess, null));
		conceptAgregateMap.put(key, amount);
		//	Get from Movement helper method
		return Optional.ofNullable(amount).orElse(Env.ZERO).doubleValue();
	} // getConcept

	/**
	 *  Helper Method : Concept Average by range from-to in periods from a different payroll
	 *  periods with values 0 -1 1, etc. actual previous one period, next period
	 *  0 corresponds to actual period
	 * @param conceptValue
	 * @param payrollValue is the value of the payroll.
	 * @param periodFrom
	 * @param periodTo the search is done by the period value, it helps to search from previous years
	 */
	public double getConceptAvg(String conceptValue, String payrollValue, int periodFrom, int periodTo) {
		return getConceptAvg(conceptValue,payrollValue,periodFrom, periodTo,true);
	}

	/**
	 *  Helper Method : Concept Average by range from-to in periods from a different payroll
	 *  periods with values 0 -1 1, etc. actual previous one period, next period
	 *  0 corresponds to actual period
	 * @param conceptValue
	 * @param payrollValue is the value of the payroll.
	 * @param periodFrom
	 * @param periodTo the search is done by the period value, it helps to search from previous years
	 * @param includeInProcess
	 */
	public double getConceptAvg(String conceptValue, String payrollValue, int periodFrom, int periodTo, boolean includeInProcess) {
		int payrollId;
		if (payrollValue == null) {
			payrollId = engineProcess.getPayrollId();
		} else {
			MHRPayroll payroll = MHRPayroll.getByValue(ruleContext.getContext(), payrollValue, null);
			if(payroll == null)
				return 0.0;
			//	
			payrollId = payroll.get_ID();
		}
		String key = "AVG|" + engineEmployee.getBusinessPartnerId() + "|" + conceptValue + "|" + payrollId + "|" + periodFrom + "|" + periodTo + "|" + includeInProcess;
		if(conceptAgregateMap.containsKey(key)) {
			return Optional.ofNullable(conceptAgregateMap.get(key)).orElse(Env.ZERO).doubleValue();
		}
		BigDecimal amount = new BigDecimal(MHRMovement.getConceptAvg(ruleContext.getContext(), conceptValue, payrollId, engineEmployee.getBusinessPartnerId(), engineProcess.getPeriodId(), periodFrom, periodTo, includeInProcess, null));
		conceptAgregateMap.put(key, amount);
		//	Get from Movement helper method
		return Optional.ofNullable(amount).orElse(Env.ZERO).doubleValue();
	} // getConcept
	
	/**
	 * Get last movement based on payroll and concept, note that if payrollvalue is null then is used current payroll value
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @param isWithValidFrom
	 * @return
	 */
	public MHRMovement getLastMovement(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		if(Util.isEmpty(payrollValue)) {
			payrollValue = engineProcess.getPayrollValue();
		}
		String key = engineEmployee.getBusinessPartnerId() + "|" + conceptValue + "|" + payrollValue + "|" + breakDate.getTime() + "|" + isWithValidFrom;
		
		//	Get from cache
		if(lastConceptMap.containsKey(key)) {
			return lastConceptMap.get(key);
		}
		MHRMovement lastMovement = MHRMovement.getLastMovement(ruleContext.getContext(), conceptValue, payrollValue, engineEmployee.getBusinessPartnerId(), breakDate, isWithValidFrom, null);
		lastConceptMap.put(key, lastMovement);
		return lastMovement;
	}
	
	/**
	 * Get last concept without break date
	 * @param conceptValue
	 * @param payrollValue
	 * @return
	 */
	public double getLastConcept(String conceptValue, String payrollValue) {
		return getLastConcept(conceptValue, payrollValue, engineProcess.getValidFrom(), false);
	}
	
	/**
	 * Get Last concept amount with break date
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public double getLastConcept(String conceptValue, String payrollValue, Timestamp breakDate) {
		return getLastConcept(conceptValue, payrollValue, breakDate, false);
	}
	
	/**
	 * Get Last concept for a engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public double getLastConcept(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		MHRMovement lastMovement = getLastMovement(conceptValue, payrollValue, breakDate, isWithValidFrom);
		//	
		if(lastMovement == null) {
			return 0.0;
		}
		//	
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue, null);
		if(MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			if(lastMovement.getQty() != null) {
				return lastMovement.getQty().doubleValue();
			}
		} else if(MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			if(lastMovement.getAmount() != null) {
				return lastMovement.getAmount().doubleValue();
			}
		}
		return 0.0;
	} // getConcept

	/**
	 * Get the typed value of a concept from the previous period of the current payroll
	 * @param conceptCode
	 * @return typed value or null
	 */
	public Object getLastConceptValue(String conceptCode) {
		return getLastConceptValue(conceptCode, null);
	}

	/**
	 * Get the typed value of a concept from the last period that ended before the current one
	 * @param conceptCode
	 * @param payrollValue payroll code, or null to use the current payroll
	 * @return typed value or null
	 */
	public Object getLastConceptValue(String conceptCode, String payrollValue) {
		if(Util.isEmpty(conceptCode)) {
			return null;
		}
		String code = conceptCode.trim();
		return getMovementValue(code, getLastMovement(code, payrollValue, engineProcess.getValidFrom(), false));
	}

	/**
	 * Get the typed value of a concept from a completed process of another payroll
	 * that covers exactly the same period dates as the current process
	 * @param conceptCode
	 * @param payrollValue
	 * @return typed value or null when the other payroll has no movement in this period
	 */
	public Object getPeriodConceptValue(String conceptCode, String payrollValue) {
		if(Util.isEmpty(conceptCode)) {
			return null;
		}
		String code = conceptCode.trim();
		return getMovementValue(code, MHRMovement.getPeriodMovement(ruleContext.getContext(), code, payrollValue,
				engineEmployee.getBusinessPartnerId(), engineProcess.getValidFrom(), engineProcess.getValidTo(), null));
	}

	private Object getMovementValue(String conceptCode, MHRMovement movement) {
		if(movement == null) {
			return null;
		}
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptCode, null);
		if(concept == null) {
			return null;
		}
		String type = concept.getColumnType();
		if(MHRConcept.COLUMNTYPE_Text.equals(type)) {
			return movement.getTextMsg();
		}
		if(MHRConcept.COLUMNTYPE_Date.equals(type)) {
			return movement.getServiceDate();
		}
		if(MHRConcept.COLUMNTYPE_Amount.equals(type)) {
			return movement.getAmount();
		}
		if(MHRConcept.COLUMNTYPE_Quantity.equals(type)) {
			return movement.getQty();
		}
		return null;
	}

	/**
	 * Get last concept date without break date
	 * @param conceptValue
	 * @param payrollValue
	 * @return
	 */
	public Timestamp getLastConceptDate(String conceptValue, String payrollValue) {
		return getLastConceptDate(conceptValue, payrollValue, engineProcess.getValidFrom(), false);
	}
	
	/**
	 * Get Last concept Date with break date
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptDate(String conceptValue, String payrollValue, Timestamp breakDate) {
		return getLastConceptDate(conceptValue, payrollValue, breakDate, false);
	}
	
	/**
	 * Get Last concept date for a engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptDate(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		MHRMovement lastMovement = getLastMovement(conceptValue, payrollValue, breakDate, isWithValidFrom);
		//	
		if(lastMovement == null) {
			return null;
		}
		return lastMovement.getServiceDate();
	} // getConcept
	
	/**
	 * Get last concept String without break date
	 * @param conceptValue
	 * @param payrollValue
	 * @return
	 */
	public String getLastConceptString(String conceptValue, String payrollValue) {
		return getLastConceptString(conceptValue, payrollValue, engineProcess.getValidFrom(), false);
	}
	
	/**
	 * Get Last concept String with break date
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public String getLastConceptString(String conceptValue, String payrollValue, Timestamp breakDate) {
		return getLastConceptString(conceptValue, payrollValue, breakDate, false);
	}
	
	/**
	 * Get Last concept String for a engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public String getLastConceptString(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		MHRMovement lastMovement = getLastMovement(conceptValue, payrollValue, breakDate, isWithValidFrom);
		//	
		if(lastMovement == null) {
			return null;
		}
		//	For all
		if(!Util.isEmpty(lastMovement.getTextMsg())) {
			return lastMovement.getTextMsg();
		}
		//	For Description (optional)
		return lastMovement.getDescription();
	} // getConcept
	
	/**
	 * Get last concept valid from without break date
	 * @param conceptValue
	 * @param payrollValue
	 * @return
	 */
	public Timestamp getLastConceptValidFrom(String conceptValue, String payrollValue) {
		return getLastConceptValidFrom(conceptValue, payrollValue, engineProcess.getValidFrom(), false);
	}
	
	/**
	 * Get Last concept valid from with break date
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptValidFrom(String conceptValue, String payrollValue, Timestamp breakDate) {
		return getLastConceptValidFrom(conceptValue, payrollValue, breakDate, false);
	}
	
	/**
	 * Get Last concept Valid From for a engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptValidFrom(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		MHRMovement lastMovement = getLastMovement(conceptValue, payrollValue, breakDate, isWithValidFrom);
		//	
		if(lastMovement == null) {
			return null;
		}
		//	Default
		return lastMovement.getValidFrom();
	} // getConcept
	
	/**
	 * Get last concept valid to without break date
	 * @param conceptValue
	 * @param payrollValue
	 * @return
	 */
	public Timestamp getLastConceptValidTo(String conceptValue, String payrollValue) {
		return getLastConceptValidTo(conceptValue, payrollValue, engineProcess.getValidFrom(), false);
	}
	
	/**
	 * Get Last concept valid to with break date
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptValidTo(String conceptValue, String payrollValue, Timestamp breakDate) {
		return getLastConceptValidTo(conceptValue, payrollValue, breakDate, false);
	}
	
	/**
	 * Get Last concept Valid To for a engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param payrollValue
	 * @param breakDate
	 * @return
	 */
	public Timestamp getLastConceptValidTo(String conceptValue, String payrollValue, Timestamp breakDate, boolean isWithValidFrom) {
		MHRMovement lastMovement = getLastMovement(conceptValue, payrollValue, breakDate, isWithValidFrom);
		//	
		if(lastMovement == null) {
			return null;
		}
		//	Default
		return lastMovement.getValidTo();
	} // getConcept
	
	/**
	 * Get Concept for this payroll for range dates
	 * @param conceptValue
	 * @param from
	 * @param to
	 * @return
	 */
	public double getConcept (String conceptValue ,Timestamp from,Timestamp to)
	{
		return getConcept(conceptValue, null , from , to, true);
	}

	/**
	 * Helper Method: gets Concept value of a payrroll between 2 dates
	 * @param conceptValue
	 * @param payrollValue
	 * @param from
	 * @param to
	 * */
	public double getConcept(String conceptValue, String payrollValue, Timestamp from, Timestamp to) {
		return getConcept(conceptValue, payrollValue , from , to , true);
	}

	/**
	 * Helper Method: gets Concept value of a payrroll between 2 dates
	 * @param conceptValue
	 * @param payrollValue
	 * @param from
	 * @param to
	 * @param includeInProcess
	 * */
	public double getConcept(String conceptValue, String payrollValue, Timestamp from, Timestamp to, boolean includeInProcess) {
		int payrollId;
		if (payrollValue == null) {
			payrollId = engineProcess.getPayrollId();
		} else {
			MHRPayroll payroll = MHRPayroll.getByValue(ruleContext.getContext(), payrollValue, null);
			if(payroll == null)
				return 0.0;
			//	
			payrollId = payroll.get_ID();
		}
		String key = "SUM|" + engineEmployee.getBusinessPartnerId() + "|" + conceptValue + "|" + payrollId + "|" + from.getTime() + "|" + to.getTime() + "|" + includeInProcess;
		if(conceptAgregateMap.containsKey(key)) {
			return Optional.ofNullable(conceptAgregateMap.get(key)).orElse(Env.ZERO).doubleValue();
		}
		BigDecimal amount = new BigDecimal(MHRMovement.getConceptSum(ruleContext.getContext(), conceptValue, payrollId, engineEmployee.getBusinessPartnerId(), from, to, includeInProcess, null));
		conceptAgregateMap.put(key, amount);
		//	Get from Movement helper method
		return Optional.ofNullable(amount).orElse(Env.ZERO).doubleValue();
	} // getConcept

	/**
	 * Helper Method: gets Concept Average value of a payrroll between 2 dates
	 * @param conceptValue
	 * @param payrollValue
	 * @param from
	 * @param to
	 * @return
	 */
	public double getConceptAvg(String conceptValue, String payrollValue, Timestamp from, Timestamp to) {
		return getConceptAvg(conceptValue, payrollValue , from , to , true);
	}

	/**
	 * Helper Method: gets Concept Average value of a payrroll between 2 dates
	 * @param conceptValue
	 * @param payrollValue
	 * @param from
	 * @param to
	 * @param includeInProcess
	 * */
	public double getConceptAvg(String conceptValue, String payrollValue, Timestamp from, Timestamp to, boolean includeInProcess) {
		int payrollId;
		if (payrollValue == null) {
			payrollId = engineProcess.getPayrollId();
		} else {
			MHRPayroll payroll = MHRPayroll.getByValue(ruleContext.getContext(), payrollValue, null);
			if(payroll == null)
				return 0.0;
			//	
			payrollId = payroll.get_ID();
		}
		String key = "AVG|" + engineEmployee.getBusinessPartnerId() + "|" + conceptValue + "|" + payrollId + "|" + from.getTime() + "|" + to.getTime() + "|" + includeInProcess;
		if(conceptAgregateMap.containsKey(key)) {
			return Optional.ofNullable(conceptAgregateMap.get(key)).orElse(Env.ZERO).doubleValue();
		}
		BigDecimal amount = new BigDecimal(MHRMovement.getConceptAvg(ruleContext.getContext(), conceptValue, payrollId, engineEmployee.getBusinessPartnerId(), from, to , includeInProcess, null));
		conceptAgregateMap.put(key, amount);
		//	Get from Movement helper method
		return Optional.ofNullable(amount).orElse(Env.ZERO).doubleValue();
	} // getConcept
	
	/**
	 * Helper Method : Get AttributeInvoice 
	 * @param conceptValue - Value to Concept
	 * @return	C_Invoice_ID, 0 if does't
	 */ 
	public int getAttributeInvoice (String conceptValue) {
		MHRAttribute attribute = getAttributeInstance(conceptValue);
		if (attribute == null)
			return 0;
		//	Get invoice
		return attribute.get_ValueAsInt("C_Invoice_ID");
	} // getAttributeInvoice
		
	/**
	 * Helper Method : Get AttributeDocType
	 * @param conceptValue - Value to Concept
	 * @return	C_DocType_ID, 0 if does't
	 */ 
	public int getAttributeDocType (String conceptValue) {
		MHRAttribute attribute = getAttributeInstance(conceptValue);
		if (attribute == null)
			return 0;
		//	
		return attribute.get_ValueAsInt("C_DocType_ID"); 
	} // getAttributeDocType

	/**
	 * Get attribute by engineEmployee.getEmployee()
	 * @param conceptValue
	 * @param engineEmployee.getBusinessPartnerId()
	 * @return
	 */
	public BigDecimal getAttributeByPartnerId(String conceptValue, Timestamp breakDate) {
		MHRConcept concept = MHRConcept.getByValue(ruleContext.getContext(), conceptValue, null);
		//	
		MHRAttribute attribute = getAttributeInstance(conceptValue, breakDate);
		if (attribute == null)
			return BigDecimal.ZERO;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount();
		//	
		return BigDecimal.ZERO;
	}

	/**
	 * Helper Method : get days from specific period
	 * @param period
	 * @return no. of days
	 */
	public double getDays (int period)
	{
		/* TODO: This getter could have an error as it's not using the parameter, and it doesn't what is specified in help */
		logger.warning("instead of using getDays in the formula it's recommended to use _DaysPeriod+1");
		return Env.getContextAsInt(ruleContext.getContext(), "_DaysPeriod") + 1;
	} // getDays
	
	/**********************************************************************************
	 * Helper Method for Get Amount from commission                                   *
	 **********************************************************************************/
	
	/**
	 * Get Commission of Employee sales representative from Commission Run
	 * @param bPartnerId
	 * @param from
	 * @param to
	 * @param docBasisType
	 * @return
	 */
	public double getCommissionAmt(int bPartnerId, Timestamp from, Timestamp to, String docBasisType) {
		BigDecimal value = MCommission.getCommissionAmt(bPartnerId, from, to, docBasisType, null);
		//	Validate value
		if(value == null)
			return 0.0;
		//	Default
		return value.doubleValue();
	}
	
	/**
	 * Get commission amount for current engineEmployee.getEmployee()
	 * @param from
	 * @param to
	 * @param docBasisType
	 * @return
	 */
	public double getCommissionAmt(Timestamp from, Timestamp to, String docBasisType) {
		return getCommissionAmt(engineEmployee.getBusinessPartnerId(), from, to, docBasisType);
	}
	
	/**
	 * Get commission amount for current engineEmployee.getEmployee() on current period with a doc basis type
	 * @param docBasisType
	 * @return
	 */
	public double getCommissionAmt(String docBasisType) {
		return getCommissionAmt(engineProcess.getValidFrom(), engineProcess.getValidTo(), docBasisType);
	}
	
	/**
	 * Get commission amount for current engineEmployee.getEmployee() and current period and all doc basis type
	 * @return
	 */
	public double getCommissionAmt() {
		return getCommissionAmt(null);
	}
	
	/**********************************************************************************
	 * Helper Method for Get Amount from time and attendance record                   *
	 **********************************************************************************/
	
	/**
	 * Helper Method : Concept by range from-to a sum of incidence
	 * @param conceptValue
	 * @param workShiftValue
	 * @param from
	 * @param to
	 * @return
	 */
	public double getIncidenceSum(String conceptValue, String workShiftValue, Timestamp from, Timestamp to) {
		return TNAUtil.getIncidenceSum(ruleContext.getContext(), conceptValue, workShiftValue, engineEmployee.getBusinessPartnerId(), from, to, null);
	} // getIncidence
	
	/**
	 * Helper Method : Concept by range from-to a sum of incidence
	 * @param conceptValue
	 * @param from
	 * @param to
	 * @return
	 */
	public double getIncidenceSum(String conceptValue, Timestamp from, Timestamp to) {
		return TNAUtil.getIncidenceSum(ruleContext.getContext(), conceptValue, null, engineEmployee.getBusinessPartnerId(), from, to, null);
	} // getIncidence
}
