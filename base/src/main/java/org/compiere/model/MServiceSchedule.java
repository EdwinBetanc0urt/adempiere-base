package org.compiere.model;

import org.adempiere.core.domains.models.X_S_ServicePlan;
import org.adempiere.core.domains.models.X_S_ServiceSchedule;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DisplayType;
import org.compiere.util.Msg;
import org.eevolution.hr.model.MHREmployee;
import org.eevolution.hr.model.MHREmployeeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Properties;

/**
 *    @author Gabriel Escalona
 */
public class MServiceSchedule extends X_S_ServiceSchedule {

public static int DAY_OF_WEEK_REFERENCE = 167;
    public MServiceSchedule(Properties ctx, int X_S_ServiceSchedule, String trxName) {
        super(ctx, X_S_ServiceSchedule, trxName);
    }
    public MServiceSchedule(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    private static final long MINUTES_PER_DAY = Duration.ofDays(1).toMinutes();
    private static final long MINUTES_PER_WEEK = MINUTES_PER_DAY * 7;

    private long calculatePlannedMinutes() {
        if (getTimeFrom() == null || getTimeTo() == null) {
            return 0;
        }
        LocalTime from = getTimeFrom().toLocalDateTime().toLocalTime();
        LocalTime to = getTimeTo().toLocalDateTime().toLocalTime();
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 0) {
            minutes += MINUTES_PER_DAY;
        }
        return minutes;
    }

    private BigDecimal calculatePlannedHours() {
        return BigDecimal.valueOf(calculatePlannedMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private long[] getWeeklyInterval() {
        if (getTimeFrom() == null || getTimeTo() == null || getDayOfWeek() == null) {
            return null;
        }
        int dayIndex;
        try {
            dayIndex = Integer.parseInt(getDayOfWeek().trim()) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        if (dayIndex < 0 || dayIndex > 6) {
            return null;
        }
        LocalTime from = getTimeFrom().toLocalDateTime().toLocalTime();
        long start = dayIndex * MINUTES_PER_DAY + from.getHour() * 60L + from.getMinute();
        return new long[] {start, start + calculatePlannedMinutes()};
    }

    private boolean overlapsWith(MServiceSchedule other) {
        long[] own = getWeeklyInterval();
        long[] candidate = other.getWeeklyInterval();
        if (own == null || candidate == null) {
            return false;
        }
        for (long shift : new long[] {-MINUTES_PER_WEEK, 0, MINUTES_PER_WEEK}) {
            if (candidate[0] + shift < own[1] && own[0] < candidate[1] + shift) {
                return true;
            }
        }
        return false;
    }

    /**
     * 	Before Save
     *	@param newRecord new
     *	@return true
     */
    @Override
    protected boolean beforeSave (boolean newRecord)
    {
        if (getS_ServicePlan_ID() <= 0) {
            throw new AdempiereException("@S_ServicePlan_ID@ @NotFound@");
        }
        BigDecimal plannedHours = calculatePlannedHours();
        setPlannedHours(plannedHours);
        if (getS_Resource_ID() > 0
                && (newRecord || is_ValueChanged(COLUMNNAME_TimeFrom) || is_ValueChanged(COLUMNNAME_TimeTo) || is_ValueChanged(COLUMNNAME_S_Resource_ID) || is_ValueChanged(COLUMNNAME_DayOfWeek))) {
            String whereClause = "S_Resource_ID = ? AND S_ServiceSchedule_ID <> ?";
            boolean hasOverlap = new Query(getCtx(), Table_Name, whereClause, get_TrxName())
                .setParameters(getS_Resource_ID(), getS_ServiceSchedule_ID())
                .setOnlyActiveRecords(true)
                .<MServiceSchedule>list()
                .stream()
                .anyMatch(this::overlapsWith);
            MResource resource = (MResource) getS_Resource();
            if (hasOverlap) {
                X_S_ServicePlan servicePlan = (X_S_ServicePlan) getS_ServicePlan();
                String servicePlanDescription = servicePlan == null ? "" : servicePlan.getDescription();
                String dayOfWeek = MRefList.getListName(getCtx(), DAY_OF_WEEK_REFERENCE, getDayOfWeek());
                String timeFromFormated = DisplayType.getDateFormat(DisplayType.Time).format(getTimeFrom());
                String timeToFormated = DisplayType.getDateFormat(DisplayType.Time).format(getTimeTo());
                String partnerName= "";
                if (getC_BPartner_ID() > 0) {
                    MBPartner partner = (MBPartner) getC_BPartner();
                    partnerName = partner.getName();
                }
                String message = Msg.parseTranslation(getCtx(), "@WeeklyPlan.Overlap@");
                String errorMessage = MessageFormat.format(message,
                        resource.getName(),
                        dayOfWeek,
                        timeFromFormated,
                        timeToFormated,
                        servicePlanDescription,
                        partnerName
                );
                throw new AdempiereException(errorMessage);
            }
            BigDecimal totalHours = new Query(getCtx(), Table_Name, whereClause, get_TrxName())
                .setParameters(getS_Resource_ID(), getS_ServiceSchedule_ID())
                .setOnlyActiveRecords(true)
                .sum("PlannedHours");
            totalHours = totalHours.add(plannedHours);
            MHREmployee employee = (MHREmployee) resource.getHR_Employee();
            MHREmployeeType employeeType = (MHREmployeeType) employee.getHR_EmployeeType();
            BigDecimal maxWeeklyHours = employeeType.getTotalDailyHours().multiply(BigDecimal.valueOf(5));
            if (totalHours.compareTo(maxWeeklyHours) > 0) {
                String maxHoursFormatted = DisplayType.getNumberFormat(DisplayType.Amount).format(maxWeeklyHours);
                String totalHoursFormatted = DisplayType.getNumberFormat(DisplayType.Amount).format(totalHours);
                String message = Msg.parseTranslation(getCtx(), "@WeeklyPlan.PlannedHoursExceeded@");
                String errorMessage = MessageFormat.format(message,
                        resource.getName(),
                        maxHoursFormatted,
                        totalHoursFormatted
                );
                throw new AdempiereException(errorMessage);
            }

        }
        return super.beforeSave(newRecord);
    }	//	beforeSave

}
