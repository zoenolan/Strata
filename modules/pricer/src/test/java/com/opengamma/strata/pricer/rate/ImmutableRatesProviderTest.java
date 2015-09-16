/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.index.FxIndices.WM_GBP_USD;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.PriceIndices.GB_RPI;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.ConstantNodalCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.value.DiscountFxForwardRates;
import com.opengamma.strata.market.value.DiscountIborIndexRates;
import com.opengamma.strata.market.value.DiscountOvernightIndexRates;
import com.opengamma.strata.market.value.ForwardPriceIndexValues;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;

/**
 * Test {@link ImmutableRatesProvider}.
 */
@Test
public class ImmutableRatesProviderTest {

  private static final LocalDate PREV_DATE = LocalDate.of(2014, 6, 27);
  private static final LocalDate VAL_DATE = LocalDate.of(2014, 6, 30);
  private static final YearMonth VAL_MONTH = YearMonth.of(2014, 6);
  private static final double FX_GBP_USD = 1.6d;
  private static final FxMatrix FX_MATRIX = FxMatrix.of(GBP, USD, FX_GBP_USD);
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;

  private static final double GBP_DSC = 0.99d;
  private static final double USD_DSC = 0.95d;
  private static final Curve DISCOUNT_CURVE_GBP = ConstantNodalCurve.of(
      Curves.zeroRates("GBP-Discount", ACT_ACT_ISDA), GBP_DSC);
  private static final Curve DISCOUNT_CURVE_USD = ConstantNodalCurve.of(
      Curves.zeroRates("USD-Discount", ACT_ACT_ISDA), USD_DSC);
  private static final Curve USD_LIBOR_CURVE = ConstantNodalCurve.of(
      Curves.zeroRates("USD-Discount", ACT_ACT_ISDA), 0.96d);
  private static final IborIndexRates LIBOR_RATES = DiscountIborIndexRates.of(
      USD_LIBOR_3M,
      LocalDateDoubleTimeSeries.of(VAL_DATE, 0.62),
      ZeroRateDiscountFactors.of(USD, VAL_DATE, USD_LIBOR_CURVE));

  private static final Curve FED_FUND_CURVE = ConstantNodalCurve.of(
      Curves.zeroRates("USD-Discount", ACT_ACT_ISDA), 0.97d);
  private static final OvernightIndexRates FED_FUND_RATES = DiscountOvernightIndexRates.of(
      USD_FED_FUND,
      LocalDateDoubleTimeSeries.of(VAL_DATE, 0.62),
      ZeroRateDiscountFactors.of(USD, VAL_DATE, FED_FUND_CURVE));

  private static final PriceIndexValues GBPRI_CURVE = ForwardPriceIndexValues.of(
      GB_RPI,
      VAL_MONTH,
      LocalDateDoubleTimeSeries.of(date(2013, 11, 30), 252),
      InterpolatedNodalCurve.of(Curves.prices("GB-RPI"), new double[] {1d, 10d}, new double[] {252d, 252d}, INTERPOLATOR));

  //-------------------------------------------------------------------------
  public void test_builder() {
    LocalDateDoubleTimeSeries ts = LocalDateDoubleTimeSeries.of(PREV_DATE, 0.62d);
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxIndexTimeSeries(WM_GBP_USD, ts)
        .build();
    assertEquals(test.getValuationDate(), VAL_DATE);
    assertEquals(ImmutableRatesProvider.meta().fxIndexTimeSeries().get(test), ImmutableMap.of(WM_GBP_USD, ts));
  }

  //-------------------------------------------------------------------------
  public void test_discountFactors() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .discountCurve(GBP, DISCOUNT_CURVE_GBP)
        .discountCurve(USD, DISCOUNT_CURVE_USD)
        .build();
    assertEquals(test.discountFactors(GBP).getCurrency(), GBP);
  }

  public void test_discountFactors_notKnown() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .build();
    assertThrowsIllegalArg(() -> test.discountFactors(GBP));
    assertThrowsIllegalArg(() -> test.discountFactor(GBP, LocalDate.of(2014, 7, 30)));
  }

  //-------------------------------------------------------------------------
  public void test_fxRate_separate() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxMatrix(FX_MATRIX)
        .build();
    assertEquals(test.fxRate(USD, GBP), 1 / FX_GBP_USD, 0d);
    assertEquals(test.fxRate(USD, USD), 1d, 0d);
  }

  public void test_fxRate_pair() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxMatrix(FX_MATRIX)
        .build();
    assertEquals(test.fxRate(CurrencyPair.of(USD, GBP)), 1 / FX_GBP_USD, 0d);
  }

  //-------------------------------------------------------------------------
  public void test_fxIndexRates() {
    LocalDateDoubleTimeSeries ts = LocalDateDoubleTimeSeries.of(VAL_DATE, 0.62d);
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxMatrix(FX_MATRIX)
        .discountCurve(GBP, DISCOUNT_CURVE_GBP)
        .discountCurve(USD, DISCOUNT_CURVE_USD)
        .fxIndexTimeSeries(WM_GBP_USD, ts)
        .build();
    assertEquals(test.fxIndexRates(WM_GBP_USD).getIndex(), WM_GBP_USD);
    assertEquals(test.fxIndexRates(WM_GBP_USD).getTimeSeries(), ts);
  }

  public void test_fxIndexRates_notKnown() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE).build();
    assertThrowsIllegalArg(() -> test.fxIndexRates(WM_GBP_USD));
  }

  //-------------------------------------------------------------------------
  public void test_fxForwardRates() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxMatrix(FX_MATRIX)
        .discountCurve(GBP, DISCOUNT_CURVE_GBP)
        .discountCurve(USD, DISCOUNT_CURVE_USD)
        .build();
    DiscountFxForwardRates res = (DiscountFxForwardRates) test.fxForwardRates(CurrencyPair.of(GBP, USD));
    assertEquals(res.getBaseCurrencyDiscountFactors(), ZeroRateDiscountFactors.of(GBP, VAL_DATE, DISCOUNT_CURVE_GBP));
    assertEquals(res.getCounterCurrencyDiscountFactors(), ZeroRateDiscountFactors.of(USD, VAL_DATE, DISCOUNT_CURVE_USD));
    assertEquals(res.getCurrencyPair(), CurrencyPair.of(GBP, USD));
    assertEquals(res.getFxRateProvider(), FX_MATRIX);
    assertEquals(res.getValuationDate(), VAL_DATE);
  }

  //-------------------------------------------------------------------------
  public void test_iborIndexRates() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .iborIndexRates(LIBOR_RATES)
        .build();
    assertEquals(test.iborIndexRates(USD_LIBOR_3M), LIBOR_RATES);
  }

  public void test_iborIndexRates_notKnown() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE).build();
    assertThrowsIllegalArg(() -> test.iborIndexRates(USD_LIBOR_3M));
  }

  //-------------------------------------------------------------------------
  public void test_overnightIndexRates() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .overnightIndexRates(FED_FUND_RATES)
        .build();
    assertEquals(test.overnightIndexRates(USD_FED_FUND), FED_FUND_RATES);
  }

  public void test_overnightIndexRates_notKnown() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE).build();
    assertThrowsIllegalArg(() -> test.overnightIndexRates(USD_FED_FUND));
  }

  //-------------------------------------------------------------------------
  public void test_priceIndexValues() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .priceIndexValues(GBPRI_CURVE)
        .build();
    assertEquals(test.priceIndexValues(GB_RPI).getIndex(), GB_RPI);
  }

  public void test_priceIndexValues_notKnown() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE).build();
    assertThrowsIllegalArg(() -> test.priceIndexValues(GB_RPI));
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    ImmutableRatesProvider test = ImmutableRatesProvider.builder(VAL_DATE)
        .fxMatrix(FX_MATRIX)
        .discountCurve(GBP, DISCOUNT_CURVE_GBP)
        .fxIndexTimeSeries(WM_GBP_USD, LocalDateDoubleTimeSeries.empty())
        .iborIndexRates(LIBOR_RATES)
        .overnightIndexRates(FED_FUND_RATES)
        .priceIndexValues(GBPRI_CURVE)
        .build();
    coverImmutableBean(test);
    ImmutableRatesProvider test2 = ImmutableRatesProvider.builder(LocalDate.of(2014, 6, 27)).build();
    coverBeanEquals(test, test2);
  }

}
