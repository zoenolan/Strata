package com.opengamma.strata.pricer.impl.volatility.local;

import java.util.function.Function;

import com.opengamma.strata.market.surface.Surface;

public interface LocalVolatilityCalculator {

  public abstract Surface getLocalVolatilityFromPrice(
      Surface prcieSurface,
      double spot,
      Function<Double, Double> interestRate,
      Function<Double, Double> dividendRate);

  public abstract Surface getLocalVolatilityFromImpliedVolatility(
      Surface impliedVolatilitySurface,
      double spot,
      Function<Double, Double> interestRate,
      Function<Double, Double> dividendRate);

}
