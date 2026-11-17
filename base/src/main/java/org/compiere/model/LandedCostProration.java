package org.compiere.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class LandedCostProration
{
	private LandedCostProration()
	{
	}

	public static List<BigDecimal> prorate(BigDecimal amount, List<BigDecimal> bases, int precision)
	{
		BigDecimal total = BigDecimal.ZERO;
		for (BigDecimal base : bases)
			total = total.add(base);
		if (total.signum() == 0)
			throw new IllegalArgumentException("Total base is zero");
		List<BigDecimal> result = new ArrayList<>();
		BigDecimal assigned = BigDecimal.ZERO;
		for (int i = 0; i < bases.size(); i++)
		{
			BigDecimal part;
			if (i == bases.size() - 1)
				part = amount.subtract(assigned);
			else
				part = amount.multiply(bases.get(i)).divide(total, MathContext.DECIMAL128).setScale(precision, RoundingMode.HALF_UP);
			assigned = assigned.add(part);
			result.add(part);
		}
		return result;
	}
}
