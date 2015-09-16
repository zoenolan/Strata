/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.calculation.rate.fra;

import static com.opengamma.strata.engine.calculation.function.FunctionUtils.toScenarioResult;
import static java.util.stream.Collectors.toSet;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import com.google.common.collect.Iterables;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.engine.calculation.DefaultSingleCalculationMarketData;
import com.opengamma.strata.engine.calculation.function.result.ScenarioResult;
import com.opengamma.strata.engine.marketdata.CalculationMarketData;
import com.opengamma.strata.engine.marketdata.SingleCalculationMarketData;
import com.opengamma.strata.finance.rate.fra.ExpandedFra;
import com.opengamma.strata.finance.rate.fra.Fra;
import com.opengamma.strata.finance.rate.fra.FraTrade;
import com.opengamma.strata.function.calculation.rate.MarketDataUtils;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.key.DiscountFactorsKey;
import com.opengamma.strata.market.key.RateIndexCurveKey;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.sensitivity.CurveGammaCalculator;

/**
 * Calculates Gamma PV01, the second-order present value sensitivity of a {@link FraTrade}
 * for each of a set of scenarios.
 * <p>
 * This implementation only supports calculating the measure when using a single curve for
 * discounting and forecasting.
 */
public class FraBucketedGammaPv01Function
    extends AbstractFraFunction<CurveCurrencyParameterSensitivities> {

  @Override
  public ScenarioResult<CurveCurrencyParameterSensitivities> execute(FraTrade trade, CalculationMarketData marketData) {
    ExpandedFra expandedFra = trade.getProduct().expand();
    return IntStream.range(0, marketData.getScenarioCount())
        .mapToObj(index -> new DefaultSingleCalculationMarketData(marketData, index))
        .map(md -> execute(trade.getProduct(), expandedFra, md))
        .collect(toScenarioResult());
  }

  @Override
  protected CurveCurrencyParameterSensitivities execute(ExpandedFra product, RatesProvider ratesProvider) {
    throw new UnsupportedOperationException("execute(FraTrade) overridden instead");
  }

  //-------------------------------------------------------------------------
  // calculate the gamma sensitivity
  private CurveCurrencyParameterSensitivities execute(
      Fra fra,
      ExpandedFra expandedFra,
      SingleCalculationMarketData marketData) {

    // find the curve and check it is valid, using cast method for better error message
    Currency currency = expandedFra.getCurrency();
    ZeroRateDiscountFactors baseDf = ZeroRateDiscountFactors.class.cast(marketData.getValue(DiscountFactorsKey.of(currency)));
    NodalCurve baseCurve = baseDf.getCurve().toNodalCurve();

    // find indices and validate there is only one curve
    Set<IborIndex> indices = new HashSet<>();
    indices.add(fra.getIndex());
    fra.getIndexInterpolated().ifPresent(indices::add);
    validateSingleCurve(indices, marketData, baseCurve);

    // calculate gamma
    CurveCurrencyParameterSensitivity gamma = CurveGammaCalculator.DEFAULT.calculateSemiParallelGamma(
        baseDf.getCurve().toNodalCurve(),
        currency,
        bumped -> calculateCurveSensitivity(expandedFra, baseDf, marketData, indices, bumped));
    return CurveCurrencyParameterSensitivities.of(gamma).multipliedBy(ONE_BASIS_POINT * ONE_BASIS_POINT);
  }

  // validates that the indices all resolve to the single specified curve
  private void validateSingleCurve(Set<IborIndex> indices, SingleCalculationMarketData marketData, NodalCurve nodalCurve) {
    Set<MarketDataKey<?>> differentForwardCurves = indices.stream()
        .map(RateIndexCurveKey::of)
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
      ExpandedFra expandedFra,
      ZeroRateDiscountFactors baseDf,
      SingleCalculationMarketData marketData,
      Set<? extends Index> indices,
      NodalCurve bumped) {

    RatesProvider ratesProvider = MarketDataUtils.toSingleCurveRatesProvider(marketData, indices, baseDf.withCurve(bumped));
    PointSensitivities pointSensitivities = pricer().presentValueSensitivity(expandedFra, ratesProvider);
    CurveCurrencyParameterSensitivities paramSensitivities = ratesProvider.curveParameterSensitivity(pointSensitivities);
    return Iterables.getOnlyElement(paramSensitivities.getSensitivities());
  }

}
