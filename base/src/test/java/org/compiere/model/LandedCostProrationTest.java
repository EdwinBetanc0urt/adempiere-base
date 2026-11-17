package org.compiere.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LandedCostProrationTest
{
	@Test
	void lastPartAbsorbsRounding()
	{
		List<BigDecimal> parts = LandedCostProration.prorate(new BigDecimal("100.00"),
			Arrays.asList(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), 2);
		assertEquals(new BigDecimal("33.33"), parts.get(0));
		assertEquals(new BigDecimal("33.33"), parts.get(1));
		assertEquals(new BigDecimal("33.34"), parts.get(2));
	}

	@Test
	void partsAlwaysAddUpToAmount()
	{
		BigDecimal amount = new BigDecimal("5433.00");
		List<BigDecimal> parts = LandedCostProration.prorate(amount,
			Arrays.asList(new BigDecimal("1.20"), new BigDecimal("61.62"), new BigDecimal("7.77")), 2);
		BigDecimal sum = BigDecimal.ZERO;
		for (BigDecimal part : parts)
			sum = sum.add(part);
		assertEquals(0, amount.compareTo(sum));
	}

	@Test
	void negativeAmountKeepsSign()
	{
		List<BigDecimal> parts = LandedCostProration.prorate(new BigDecimal("-90.00"),
			Arrays.asList(new BigDecimal("1"), new BigDecimal("2")), 2);
		assertEquals(new BigDecimal("-30.00"), parts.get(0));
		assertEquals(new BigDecimal("-60.00"), parts.get(1));
	}

	@Test
	void zeroTotalBaseIsRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> LandedCostProration.prorate(BigDecimal.TEN,
			Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO), 2));
	}
}
