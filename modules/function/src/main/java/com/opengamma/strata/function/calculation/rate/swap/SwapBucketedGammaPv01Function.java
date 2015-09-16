/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.calculation.rate.swap;

import static com.opengamma.strata.engine.calculation.function.FunctionUtils.toScenarioResult;
import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.IntStream;

import com.google.common.collect.Iterables;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.engine.calculation.DefaultSingleCalculationMarketData;
import com.opengamma.strata.engine.calculation.function.result.ScenarioResult;
import com.opengamma.strata.engine.marketdata.CalculationMarketData;
import com.opengamma.strata.engine.marketdata.SingleCalculationMarketData;
import com.opengamma.strata.finance.rate.swap.ExpandedSwap;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.finance.rate.swap.SwapTrade;
import com.opengamma.strata.function.calculation.rate.MarketDataUtils;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.key.DiscountFactorsKey;
import com.opengamma.strata.market.key.MarketDataKeys;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.sensitivity.CurveGammaCalculator;

/**
 * Calculates Gamma PV01, the second-order present value sensitivity of a {@link SwapTrade}
 * for each of a set of scenarios.
 * <p>
 * This implementation only supports calculating the measure when using a single curve for
 * discounting and forecasting.
 */
public class SwapBucketedGammaPv01Function
    extends AbstractSwapFunction<CurveCurrencyParameterSensitivities> {

  @Override
  public ScenarioResult<CurveCurrencyParameterSensitivities> execute(SwapTrade trade, CalculationMarketData marketData) {
    ExpandedSwap expandedSwap = trade.getProduct().expand();
    return IntStream.range(0, marketData.getScenarioCount())
        .mapToObj(index -> new DefaultSingleCalculationMarketData(marketData, index))
        .map(md -> execute(trade.getProduct(), expandedSwap, md))
        .collect(toScenarioResult());
  }

  @Override
  protected CurveCurrencyParameterSensitivities execute(ExpandedSwap product, RatesProvider provider) {
    throw new UnsupportedOperationException("execute(SwapTrade) overridden instead");
  }

  //-------------------------------------------------------------------------
  // calculate the gamma sensitivity
  private CurveCurrencyParameterSensitivities execute(
      Swap swap,
      ExpandedSwap expandedSwap,
      SingleCalculationMarketData marketData) {

    // find the curve and check it is valid, using cast method for better error message
    if (swap.isCrossCurrency()) {
      throw new IllegalArgumentException("Implementation only supports a single curve, but swap is cross-currency");
    }
    Currency currency = swap.getLegs().get(0).getCurrency();
    ZeroRateDiscountFactors baseDf = ZeroRateDiscountFactors.class.cast(marketData.getValue(DiscountFactorsKey.of(currency)));
    NodalCurve baseCurve = baseDf.getCurve().toNodalCurve();

    // find indices and validate there is only one curve
    Set<Index> indices = swap.allIndices();
    validateSingleCurve(indices, marketData, baseCurve);

    // calculate gamma
    CurveCurrencyParameterSensitivity gamma = CurveGammaCalculator.DEFAULT.calculateSemiParallelGamma(
        baseCurve,
        currency,
        c -> calculateCurveSensitivity(expandedSwap, baseDf, marketData, indices, c));
    return CurveCurrencyParameterSensitivities.of(gamma).multipliedBy(ONE_BASIS_POINT * ONE_BASIS_POINT);
  }

  // validates that the indices all resolve to the single specified curve
  private void validateSingleCurve(Set<Index> indices, SingleCalculationMarketData marketData, NodalCurve nodalCurve) {
    Set<MarketDataKey<?>> differentForwardCurves = indices.stream()
        .map(MarketDataKeys::indexCurve)
        .filter(k -> !nodalCurve.equals(marketData.getValue(k)))
        .collect(toSet());
    if (!differentForwardCurves.isEmpty()) {
      throw new IllegalArgumentException(
          Messages.format("Implementation only supports a single curve, but discounting curve is different from " +
              "index curves for indices: {}", differentForwardCurves));
    }
  }

  // calculates the sensitivity
  private CurveCurrencyParameterSensitivity calculateCurveSensitivity(
      ExpandedSwap expandedSwap,
      ZeroRateDiscountFactors baseDf,
      SingleCalculationMarketData marketData,
      Set<? extends Index> indices,
      NodalCurve bumped) {

    RatesProvider ratesProvider = MarketDataUtils.toSingleCurveRatesProvider(marketData, indices, baseDf.withCurve(bumped));
    PointSensitivities pointSensitivities = pricer().presentValueSensitivity(expandedSwap, ratesProvider).build();
    CurveCurrencyParameterSensitivities paramSensitivities = ratesProvider.curveParameterSensitivity(pointSensitivities);
    return Iterables.getOnlyElement(paramSensitivities.getSensitivities());
  }

}
