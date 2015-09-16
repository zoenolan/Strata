/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.sensitivity;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.DiscountIborIndexRates;
import com.opengamma.strata.market.value.DiscountOvernightIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.datasets.RatesProviderDataSets;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Tests {@link RatesFiniteDifferenceSensitivityCalculator}.
 */
public class RatesFiniteDifferenceSensitivityCalculatorTest {

  private static final RatesFiniteDifferenceSensitivityCalculator FD_CALCULATOR =
      RatesFiniteDifferenceSensitivityCalculator.DEFAULT;

  private static final double TOLERANCE_DELTA = 1.0E-8;

  @Test
  public void sensitivity_single_curve() {
    CurveCurrencyParameterSensitivities sensiComputed = FD_CALCULATOR.sensitivity(RatesProviderDataSets.SINGLE_USD, this::fn);
    double[] times = RatesProviderDataSets.TIMES_1;
    assertEquals(sensiComputed.size(), 1);
    double[] s = sensiComputed.getSensitivities().get(0).getSensitivity();
    assertEquals(s.length, times.length);
    for (int i = 0; i < times.length; i++) {
      assertEquals(s[i], times[i] * 4.0d, TOLERANCE_DELTA);
    }
  }

  @Test
  public void sensitivity_multi_curve() {
    CurveCurrencyParameterSensitivities sensiComputed = FD_CALCULATOR.sensitivity(RatesProviderDataSets.MULTI_USD, this::fn);
    double[] times1 = RatesProviderDataSets.TIMES_1;
    double[] times2 = RatesProviderDataSets.TIMES_2;
    double[] times3 = RatesProviderDataSets.TIMES_3;
    assertEquals(sensiComputed.size(), 3);
    double[] s1 = sensiComputed.getSensitivity(RatesProviderDataSets.USD_DSC_NAME, USD).getSensitivity();
    assertEquals(s1.length, times1.length);
    for (int i = 0; i < times1.length; i++) {
      assertEquals(times1[i] * 2.0d, s1[i], TOLERANCE_DELTA);
    }
    double[] s2 = sensiComputed.getSensitivity(RatesProviderDataSets.USD_L3_NAME, USD).getSensitivity();
    assertEquals(s2.length, times2.length);
    for (int i = 0; i < times2.length; i++) {
      assertEquals(times2[i], s2[i], TOLERANCE_DELTA);
    }
    double[] s3 = sensiComputed.getSensitivity(RatesProviderDataSets.USD_L6_NAME, USD).getSensitivity();
    assertEquals(s3.length, times3.length);
    for (int i = 0; i < times3.length; i++) {
      assertEquals(times3[i], s3[i], TOLERANCE_DELTA);
    }
  }

  // private function for testing. Returns the sum of rates multiplied by time
  private CurrencyAmount fn(ImmutableRatesProvider provider) {
    double result = 0.0;
    // Currency
    for (DiscountFactors df : provider.getDiscountFactors().values()) {
      NodalCurve curveInt = checkInterpolated(df);
      result += sumProduct(curveInt);
    }
    // Index
    for (IborIndexRates rates : provider.getIborIndexRates().values()) {
      NodalCurve curveInt = checkInterpolated(rates);
      result += sumProduct(curveInt);
    }
    for (OvernightIndexRates rates : provider.getOvernightIndexRates().values()) {
      NodalCurve curveInt = checkInterpolated(rates);
      result += sumProduct(curveInt);
    }
    return CurrencyAmount.of(USD, result);
  }

  // compute the sum of the product of times and rates
  private double sumProduct(NodalCurve curveInt) {
    double result = 0.0;
    double[] x = curveInt.getXValues();
    double[] y = curveInt.getYValues();
    int nbNodePoint = x.length;
    for (int i = 0; i < nbNodePoint; i++) {
      result += x[i] * y[i];
    }
    return result;
  }

  // check that the curve is InterpolatedNodalCurve
  private NodalCurve checkInterpolated(DiscountFactors df) {
    ZeroRateDiscountFactors zrdf = (ZeroRateDiscountFactors) df;
    ArgChecker.isTrue(zrdf.getCurve() instanceof NodalCurve, "Curve should be a NodalCurve");
    return (NodalCurve) zrdf.getCurve();
  }

  // check that the curve is InterpolatedNodalCurve
  private NodalCurve checkInterpolated(IborIndexRates rates) {
    DiscountIborIndexRates drates = (DiscountIborIndexRates) rates;
    return checkInterpolated(drates.getDiscountFactors());
  }

  // check that the curve is InterpolatedNodalCurve
  private NodalCurve checkInterpolated(OvernightIndexRates rates) {
    DiscountOvernightIndexRates drates = (DiscountOvernightIndexRates) rates;
    return checkInterpolated(drates.getDiscountFactors());
  }

}
