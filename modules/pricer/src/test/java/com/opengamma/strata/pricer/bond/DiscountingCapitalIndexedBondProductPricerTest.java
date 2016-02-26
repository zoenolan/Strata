/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.bond;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ICMA;
import static com.opengamma.strata.basics.date.HolidayCalendars.USNY;
import static com.opengamma.strata.basics.index.PriceIndices.US_CPI_U;
import static com.opengamma.strata.market.value.CompoundedRateType.CONTINUOUS;
import static com.opengamma.strata.market.value.CompoundedRateType.PERIODIC;
import static com.opengamma.strata.product.bond.YieldConvention.US_IL_REAL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.Period;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.value.CompoundedRateType;
import com.opengamma.strata.market.view.IssuerCurveDiscountFactors;
import com.opengamma.strata.pricer.impl.bond.DiscountingCapitalIndexedBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;
import com.opengamma.strata.pricer.rate.RateObservationFn;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.Security;
import com.opengamma.strata.product.UnitSecurity;
import com.opengamma.strata.product.bond.CapitalIndexedBond;
import com.opengamma.strata.product.bond.CapitalIndexedBondPaymentPeriod;
import com.opengamma.strata.product.bond.ExpandedCapitalIndexedBond;
import com.opengamma.strata.product.rate.RateObservation;
import com.opengamma.strata.product.swap.InflationRateCalculation;

/**
 * Test {@link DiscountingCapitalIndexedBondProductPricer}.
 */
@Test
public class DiscountingCapitalIndexedBondProductPricerTest {

  private static final double NOTIONAL = 10_000_000d;
  private static final double START_INDEX = 198.47742;
  private static final double REAL_COUPON_VALUE = 0.01;
  private static final ValueSchedule REAL_COUPON = ValueSchedule.of(REAL_COUPON_VALUE);
  private static final InflationRateCalculation RATE_CALC = InflationRateCalculation.builder()
      .gearing(REAL_COUPON)
      .index(US_CPI_U)
      .lag(Period.ofMonths(3))
      .interpolated(true)
      .build();
  private static final BusinessDayAdjustment EX_COUPON_ADJ =
      BusinessDayAdjustment.of(BusinessDayConventions.PRECEDING, USNY);
  private static final DaysAdjustment SETTLE_OFFSET = DaysAdjustment.ofBusinessDays(2, USNY);
  private static final StandardId LEGAL_ENTITY = CapitalIndexedBondCurveDataSet.getIssuerId();
  private static final LocalDate START = LocalDate.of(2006, 1, 15);
  private static final LocalDate END = LocalDate.of(2016, 1, 15);
  private static final Frequency FREQUENCY = Frequency.P6M;
  private static final BusinessDayAdjustment BUSINESS_ADJUST =
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, USNY);
  private static final PeriodicSchedule SCHEDULE =
      PeriodicSchedule.of(START, END, FREQUENCY, BUSINESS_ADJUST, StubConvention.NONE, RollConventions.NONE);
  private static final CapitalIndexedBond PRODUCT = CapitalIndexedBond.builder()
      .notional(NOTIONAL)
      .currency(USD)
      .dayCount(ACT_ACT_ICMA)
      .rateCalculation(RATE_CALC)
      .legalEntityId(LEGAL_ENTITY)
      .yieldConvention(US_IL_REAL)
      .settlementDateOffset(SETTLE_OFFSET)
      .periodicSchedule(SCHEDULE)
      .startIndexValue(START_INDEX)
      .build();
  private static final DaysAdjustment EX_COUPON = DaysAdjustment.ofCalendarDays(-5, EX_COUPON_ADJ);
  private static final CapitalIndexedBond PRODUCT_EX_COUPON = CapitalIndexedBond.builder()
      .notional(NOTIONAL)
      .currency(USD)
      .dayCount(ACT_ACT_ICMA)
      .rateCalculation(RATE_CALC)
      .legalEntityId(LEGAL_ENTITY)
      .yieldConvention(US_IL_REAL)
      .settlementDateOffset(SETTLE_OFFSET)
      .periodicSchedule(SCHEDULE)
      .exCouponPeriod(EX_COUPON)
      .startIndexValue(START_INDEX)
      .build();
  private static final StandardId SECURITY_ID = StandardId.of("OG-Ticker", "BOND1");
  private static final Security<CapitalIndexedBond> SECURITY =
      UnitSecurity.builder(PRODUCT).standardId(SECURITY_ID).build();
  private static final Security<CapitalIndexedBond> SECURITY_EX_COUPON =
      UnitSecurity.builder(PRODUCT_EX_COUPON).standardId(SECURITY_ID).build();
  // detachment date (for nonzero ex-coupon days) < valuation date < payment date
  private static final LocalDate VALUATION = LocalDate.of(2014, 7, 10);
  private static final LocalDateDoubleTimeSeries TS = CapitalIndexedBondCurveDataSet.getTimeSeries(VALUATION);
  private static final ImmutableRatesProvider RATES_PROVIDER =
      CapitalIndexedBondCurveDataSet.getRatesProvider(VALUATION, TS);
  private static final LegalEntityDiscountingProvider ISSUER_RATES_PROVIDER =
      CapitalIndexedBondCurveDataSet.getLegalEntityDiscountingProvider(VALUATION);
  private static final IssuerCurveDiscountFactors ISSUER_DISCOUNT_FACTORS =
      CapitalIndexedBondCurveDataSet.getIssuerCurveDiscountFactors(VALUATION);
  // valuation date = payment date
  private static final LocalDate VALUATION_ON_PAY = LocalDate.of(2014, 1, 15);
  private static final LocalDateDoubleTimeSeries TS_ON_PAY =
      CapitalIndexedBondCurveDataSet.getTimeSeries(VALUATION_ON_PAY);
  private static final ImmutableRatesProvider RATES_PROVIDER_ON_PAY =
      CapitalIndexedBondCurveDataSet.getRatesProvider(VALUATION_ON_PAY, TS_ON_PAY);
  private static final LegalEntityDiscountingProvider ISSUER_RATES_PROVIDER_ON_PAY =
      CapitalIndexedBondCurveDataSet.getLegalEntityDiscountingProvider(VALUATION_ON_PAY);
  private static final IssuerCurveDiscountFactors ISSUER_DISCOUNT_FACTORS_ON_PAY =
      CapitalIndexedBondCurveDataSet.getIssuerCurveDiscountFactors(VALUATION_ON_PAY);

  private static final double Z_SPREAD = 0.015;
  private static final int PERIOD_PER_YEAR = 4;

  private static final double TOL = 1.0e-12;
  private static final double EPS = 1.0e-6;
  private static final DiscountingCapitalIndexedBondProductPricer PRICER = DiscountingCapitalIndexedBondProductPricer.DEFAULT;
  private static final DiscountingCapitalIndexedBondPaymentPeriodPricer PERIOD_PRICER =
      DiscountingCapitalIndexedBondPaymentPeriodPricer.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CAL =
      new RatesFiniteDifferenceSensitivityCalculator(EPS);

  //-------------------------------------------------------------------------
  public void test_getter() {
    assertEquals(PRICER.getPeriodPricer(), PERIOD_PRICER);
  }

  //-------------------------------------------------------------------------
  public void test_presentValue() {
    CurrencyAmount computed = PRICER.presentValue(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    ExpandedCapitalIndexedBond expanded = PRODUCT.expand();
    double expected = PERIOD_PRICER.presentValue(expanded.getNominalPayment(), RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS);
    int size = expanded.getPeriodicPayments().size();
    for (int i = 16; i < size; ++i) {
      CapitalIndexedBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      expected += PERIOD_PRICER.presentValue(payment, RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS);
    }
    assertEquals(computed.getAmount(), expected, TOL * NOTIONAL);
  }

  public void test_presentValue_exCoupon() {
    CurrencyAmount computed = PRICER.presentValue(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    ExpandedCapitalIndexedBond expanded = PRODUCT_EX_COUPON.expand();
    double expected = PERIOD_PRICER.presentValue(expanded.getNominalPayment(), RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS);
    int size = expanded.getPeriodicPayments().size();
    for (int i = 17; i < size; ++i) { // in ex-coupon period
      CapitalIndexedBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      expected += PERIOD_PRICER.presentValue(payment, RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS);
    }
    assertEquals(computed.getAmount(), expected, TOL * NOTIONAL);
  }

  public void test_presentValueWithZSpread() {
    CurrencyAmount computed = PRICER.presentValueWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    ExpandedCapitalIndexedBond expanded = PRODUCT.expand();
    double expected = PERIOD_PRICER.presentValueWithZSpread(expanded.getNominalPayment(), RATES_PROVIDER,
        ISSUER_DISCOUNT_FACTORS, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    int size = expanded.getPeriodicPayments().size();
    for (int i = 16; i < size; ++i) {
      CapitalIndexedBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      expected += PERIOD_PRICER.presentValueWithZSpread(
          payment, RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    }
    assertEquals(computed.getAmount(), expected, TOL * NOTIONAL);
  }

  public void test_presentValueWithZSpread_exCoupon() {
    CurrencyAmount computed = PRICER.presentValueWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0);
    ExpandedCapitalIndexedBond expanded = PRODUCT_EX_COUPON.expand();
    double expected = PERIOD_PRICER.presentValueWithZSpread(expanded.getNominalPayment(), RATES_PROVIDER,
        ISSUER_DISCOUNT_FACTORS, Z_SPREAD, CONTINUOUS, 0);
    int size = expanded.getPeriodicPayments().size();
    for (int i = 17; i < size; ++i) {  // in ex-coupon period
      CapitalIndexedBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      expected += PERIOD_PRICER.presentValueWithZSpread(
          payment, RATES_PROVIDER, ISSUER_DISCOUNT_FACTORS, Z_SPREAD, CONTINUOUS, 0);
    }
    assertEquals(computed.getAmount(), expected, TOL * NOTIONAL);
  }

  //-------------------------------------------------------------------------
  public void test_presentValueSensitivity() {
    PointSensitivities point = PRICER.presentValueSensitivity(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPvSensitivity(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_presentValueSensitivity_exCoupon() {
    PointSensitivities point =
        PRICER.presentValueSensitivity(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected =
        fdPvSensitivity(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_presentValueSensitivityWithZSpread() {
    PointSensitivities point = PRICER.presentValueSensitivityWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPvSensitivityWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_presentValueSensitivityWithZSpread_exCoupon() {
    PointSensitivities point = PRICER.presentValueSensitivityWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPvSensitivityWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  //-------------------------------------------------------------------------
  public void test_zSpreadFromCurvesAndPV() {
    CurrencyAmount pv = PRICER.presentValueWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    double computed = PRICER.zSpreadFromCurvesAndPV(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, pv, PERIODIC, PERIOD_PER_YEAR);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  public void test_zSpreadFromCurvesAndPV_exCoupon() {
    CurrencyAmount pv = PRICER.presentValueWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0);
    double computed = PRICER.zSpreadFromCurvesAndPV(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, pv, CONTINUOUS, 0);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyNominalPriceFromCurves() {
    double computed = PRICER.dirtyNominalPriceFromCurves(SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    CapitalIndexedBond product = SECURITY.getProduct();
    LocalDate settlement = SETTLE_OFFSET.adjust(VALUATION);
    double df =
        ISSUER_RATES_PROVIDER.repoCurveDiscountFactors(SECURITY_ID, LEGAL_ENTITY, USD).discountFactor(settlement);
    double expected =
        PRICER.presentValue(product, RATES_PROVIDER, ISSUER_RATES_PROVIDER, settlement).getAmount() / NOTIONAL / df;
    assertEquals(computed, expected, TOL);
  }

  public void test_dirtyNominalPriceFromCurves_exCoupon() {
    double computed = PRICER.dirtyNominalPriceFromCurves(SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    CapitalIndexedBond product = SECURITY_EX_COUPON.getProduct();
    LocalDate settlement = SETTLE_OFFSET.adjust(VALUATION);
    double df =
        ISSUER_RATES_PROVIDER.repoCurveDiscountFactors(SECURITY_ID, LEGAL_ENTITY, USD).discountFactor(settlement);
    double expected =
        PRICER.presentValue(product, RATES_PROVIDER, ISSUER_RATES_PROVIDER, settlement).getAmount() / NOTIONAL / df;
    assertEquals(computed, expected, TOL);
  }

  public void test_dirtyNominalPriceFromCurvesWithZSpread() {
    double computed = PRICER.dirtyNominalPriceFromCurvesWithZSpread(
        SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0);
    CapitalIndexedBond product = SECURITY.getProduct();
    LocalDate settlement = SETTLE_OFFSET.adjust(VALUATION);
    double df =
        ISSUER_RATES_PROVIDER.repoCurveDiscountFactors(SECURITY_ID, LEGAL_ENTITY, USD).discountFactor(settlement);
    double expected = PRICER.presentValueWithZSpread(product, RATES_PROVIDER, ISSUER_RATES_PROVIDER, settlement,
        Z_SPREAD, CONTINUOUS, 0).getAmount() / NOTIONAL / df;
    assertEquals(computed, expected, TOL);
  }

  public void test_dirtyNominalPriceFromCurvesWithZSpread_exCoupon() {
    double computed = PRICER.dirtyNominalPriceFromCurvesWithZSpread(
        SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    CapitalIndexedBond product = SECURITY_EX_COUPON.getProduct();
    LocalDate settlement = SETTLE_OFFSET.adjust(VALUATION);
    double df =
        ISSUER_RATES_PROVIDER.repoCurveDiscountFactors(SECURITY_ID, LEGAL_ENTITY, USD).discountFactor(settlement);
    double expected = PRICER.presentValueWithZSpread(product, RATES_PROVIDER, ISSUER_RATES_PROVIDER, settlement,
        Z_SPREAD, PERIODIC, PERIOD_PER_YEAR).getAmount() / NOTIONAL / df;
    assertEquals(computed, expected, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyPriceNominalPriceFromCurvesSensitivity() {
    PointSensitivities point =
        PRICER.dirtyNominalPriceSensitivity(SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPriceSensitivity(SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_dirtyPriceNominalPriceFromCurvesSensitivity_exCoupon() {
    PointSensitivities point =
        PRICER.dirtyNominalPriceSensitivity(SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected =
        fdPriceSensitivity(SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_dirtyPriceNominalPriceFromCurvesSensitivityWithZSpread() {
    PointSensitivities point = PRICER.dirtyNominalPriceSensitivityWithZSpread(
        SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPriceSensitivityWithZSpread(
        SECURITY, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  public void test_dirtyPriceNominalPriceFromCurvesSensitivityWithZSpread_exCoupon() {
    PointSensitivities point = PRICER.dirtyNominalPriceSensitivityWithZSpread(
        SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0).build();
    CurveCurrencyParameterSensitivities computed1 = RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities computed2 = ISSUER_RATES_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = fdPriceSensitivityWithZSpread(
        SECURITY_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0);
    assertTrue(expected.equalWithTolerance(computed1.combinedWith(computed2), EPS * NOTIONAL));
  }

  //-------------------------------------------------------------------------
  public void test_currencyExposure() {
    MultiCurrencyAmount computed = PRICER.currencyExposure(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, VALUATION);
    PointSensitivities point = PRICER.presentValueSensitivity(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    MultiCurrencyAmount expected = RATES_PROVIDER.currencyExposure(point)
        .plus(PRICER.presentValue(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER));
    assertEquals(computed.getCurrencies().size(), 1);
    assertEquals(computed.getAmount(USD).getAmount(), expected.getAmount(USD).getAmount(), NOTIONAL * TOL);
  }

  public void test_currencyExposure_exCoupon() {
    MultiCurrencyAmount computed =
        PRICER.currencyExposure(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, VALUATION);
    PointSensitivities point =
        PRICER.presentValueSensitivity(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER).build();
    MultiCurrencyAmount expected = RATES_PROVIDER.currencyExposure(point)
        .plus(PRICER.presentValue(PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER));
    assertEquals(computed.getCurrencies().size(), 1);
    assertEquals(computed.getAmount(USD).getAmount(), expected.getAmount(USD).getAmount(), NOTIONAL * TOL);
  }

  public void test_currencyExposureWithZSpread() {
    MultiCurrencyAmount computed = PRICER.currencyExposureWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, VALUATION, Z_SPREAD, CONTINUOUS, 0);
    PointSensitivities point = PRICER.presentValueSensitivityWithZSpread(
        PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0).build();
    MultiCurrencyAmount expected = RATES_PROVIDER.currencyExposure(point).plus(
        PRICER.presentValueWithZSpread(PRODUCT, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, CONTINUOUS, 0));
    assertEquals(computed.getCurrencies().size(), 1);
    assertEquals(computed.getAmount(USD).getAmount(), expected.getAmount(USD).getAmount(), NOTIONAL * TOL);
  }

  public void test_currencyExposureWithZSpread_exCoupon() {
    MultiCurrencyAmount computed = PRICER.currencyExposureWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, VALUATION, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR);
    PointSensitivities point = PRICER.presentValueSensitivityWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR).build();
    MultiCurrencyAmount expected = RATES_PROVIDER.currencyExposure(point).plus(PRICER.presentValueWithZSpread(
        PRODUCT_EX_COUPON, RATES_PROVIDER, ISSUER_RATES_PROVIDER, Z_SPREAD, PERIODIC, PERIOD_PER_YEAR));
    assertEquals(computed.getCurrencies().size(), 1);
    assertEquals(computed.getAmount(USD).getAmount(), expected.getAmount(USD).getAmount(), NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_currentCash() {
    CurrencyAmount computed = PRICER.currentCash(PRODUCT, RATES_PROVIDER, VALUATION);
    assertEquals(computed.getAmount(), 0d);
  }

  public void test_currentCash_exCoupon() {
    CurrencyAmount computed = PRICER.currentCash(PRODUCT_EX_COUPON, RATES_PROVIDER, VALUATION);
    assertEquals(computed.getAmount(), 0d);
  }

  public void test_currentCash_onPayment() {
    CurrencyAmount computed = PRICER.currentCash(PRODUCT, RATES_PROVIDER_ON_PAY, VALUATION_ON_PAY.minusDays(7));
    double expected = PERIOD_PRICER.forecastValue(PRODUCT.expand().getPeriodicPayments().get(15), RATES_PROVIDER_ON_PAY);
    assertEquals(computed.getAmount(), expected);
  }

  public void test_currentCash_onPayment_exCoupon() {
    CurrencyAmount computed = PRICER.currentCash(PRODUCT_EX_COUPON, RATES_PROVIDER_ON_PAY, VALUATION_ON_PAY);
    assertEquals(computed.getAmount(), 0d);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyPriceFromStandardYield() {
    double yield = 0.0175;
    LocalDate standardSettle = SETTLE_OFFSET.adjust(VALUATION);
    double computed = PRICER.dirtyPriceFromStandardYield(PRODUCT, RATES_PROVIDER, standardSettle, yield);
    Schedule sch = SCHEDULE.createSchedule().toUnadjusted();
    CapitalIndexedBondPaymentPeriod period = PRODUCT.expand().getPeriodicPayments().get(16);
    double factorPeriod =
        ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), period.getUnadjustedEndDate(), sch);
    double factorSpot = ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), standardSettle, sch);
    double factorToNext = (factorPeriod - factorSpot) / factorPeriod;
    double dscFactor = 1d / (1d + 0.5 * yield);
    double expected = Math.pow(dscFactor, 3);
    for (int i = 0; i < 4; ++i) {
      expected += REAL_COUPON_VALUE * Math.pow(dscFactor, i);
    }
    expected *= Math.pow(dscFactor, factorToNext);
    assertEquals(computed, expected, TOL);
  }

  public void test_modifiedDurationFromStandardYield() {
    double yield = 0.0175;
    LocalDate standardSettle = SETTLE_OFFSET.adjust(VALUATION);
    double computed =
        PRICER.modifiedDurationFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield);
    double price = PRICER.dirtyPriceFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield);
    double up = PRICER.dirtyPriceFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield + EPS);
    double dw = PRICER.dirtyPriceFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield - EPS);
    double expected = -0.5 * (up - dw) / price / EPS;
    assertEquals(computed, expected, EPS);

  }

  public void test_convexityFromStandardYield() {
    double yield = 0.0175;
    LocalDate standardSettle = SETTLE_OFFSET.adjust(VALUATION);
    double computed = PRICER.convexityFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield);
    double md = PRICER.modifiedDurationFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield);
    double up = PRICER.modifiedDurationFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield + EPS);
    double dw = PRICER.modifiedDurationFromStandardYield(PRODUCT_EX_COUPON, RATES_PROVIDER, standardSettle, yield - EPS);
    double expected = -0.5 * (up - dw) / EPS + md * md;
    assertEquals(computed, expected, EPS);
    double computed1 = PRICER.convexityFromStandardYield(PRODUCT, RATES_PROVIDER, VALUATION, yield);
    double md1 = PRICER.modifiedDurationFromStandardYield(PRODUCT, RATES_PROVIDER, VALUATION, yield);
    double up1 = PRICER.modifiedDurationFromStandardYield(PRODUCT, RATES_PROVIDER, VALUATION, yield + EPS);
    double dw1 = PRICER.modifiedDurationFromStandardYield(PRODUCT, RATES_PROVIDER, VALUATION, yield - EPS);
    double expected1 = -0.5 * (up1 - dw1) / EPS + md1 * md1;
    assertEquals(computed1, expected1, EPS);
  }

  //-------------------------------------------------------------------------
  public void test_accruedInterest() {
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    double computed = PRICER.accruedInterest(PRODUCT, refDate);
    Schedule sch = SCHEDULE.createSchedule().toUnadjusted();
    CapitalIndexedBondPaymentPeriod period = PRODUCT.expand().getPeriodicPayments().get(16);
    double factor = ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), refDate, sch);
    assertEquals(computed, factor * REAL_COUPON_VALUE * NOTIONAL * 2d, TOL * REAL_COUPON_VALUE * NOTIONAL);
  }

  public void test_accruedInterest_onPayment() {
    CapitalIndexedBondPaymentPeriod period = PRODUCT.expand().getPeriodicPayments().get(16);
    LocalDate refDate = period.getPaymentDate();
    double computed = PRICER.accruedInterest(PRODUCT, refDate);
    assertEquals(computed, 0d, TOL * REAL_COUPON_VALUE * NOTIONAL);
  }

  public void test_accruedInterest_before() {
    LocalDate refDate = LocalDate.of(2003, 1, 22);
    double computed = PRICER.accruedInterest(PRODUCT, refDate);
    assertEquals(computed, 0d, TOL * REAL_COUPON_VALUE * NOTIONAL);
  }

  public void test_accruedInterest_exCoupon_in() {
    CapitalIndexedBondPaymentPeriod period = PRODUCT_EX_COUPON.expand().getPeriodicPayments().get(16);
    LocalDate refDate = period.getDetachmentDate();
    double computed = PRICER.accruedInterest(PRODUCT_EX_COUPON, refDate);
    Schedule sch = SCHEDULE.createSchedule().toUnadjusted();
    double factor = ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), refDate, sch);
    double factorTotal =
        ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), period.getUnadjustedEndDate(), sch);
    assertEquals(computed, (factor - factorTotal) * REAL_COUPON_VALUE * NOTIONAL * 2d,
        TOL * REAL_COUPON_VALUE * NOTIONAL);
  }

  public void test_accruedInterest_exCoupon_out() {
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    CapitalIndexedBondPaymentPeriod period = PRODUCT_EX_COUPON.expand().getPeriodicPayments().get(16);
    double computed = PRICER.accruedInterest(PRODUCT_EX_COUPON, refDate);
    Schedule sch = SCHEDULE.createSchedule().toUnadjusted();
    double factor = ACT_ACT_ICMA.relativeYearFraction(period.getUnadjustedStartDate(), refDate, sch);
    assertEquals(computed, factor * REAL_COUPON_VALUE * NOTIONAL * 2d, TOL * REAL_COUPON_VALUE * NOTIONAL);
  }

  //-------------------------------------------------------------------------
  public void test_cleanRealPrice_dirtyRealPrice() {
    double dirtyRealPrice = 1.055;
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    double cleanRealPrice = PRICER.cleanRealPriceFromDirtyRealPrice(PRODUCT, refDate, dirtyRealPrice);
    double expected = dirtyRealPrice - PRICER.accruedInterest(PRODUCT, refDate) / NOTIONAL;
    assertEquals(cleanRealPrice, expected, TOL);
    assertEquals(PRICER.dirtyRealPriceFromCleanRealPrice(PRODUCT, refDate, cleanRealPrice), dirtyRealPrice, TOL);
  }

  public void test_realPrice_nominalPrice_settleBefore() {
    double realPrice = 1.055;
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    double nominalPrice = PRICER.nominalPriceFromRealPrice(PRODUCT, RATES_PROVIDER_ON_PAY, refDate, realPrice);
    RateObservation obs = RATE_CALC.createRateObservation(refDate, START_INDEX);
    double refRate = RateObservationFn.instance().rate(obs, null, null, RATES_PROVIDER_ON_PAY);
    double expected = realPrice * (refRate + 1d);
    assertEquals(nominalPrice, expected, TOL);
    assertEquals(PRICER.realPriceFromNominalPrice(PRODUCT, RATES_PROVIDER_ON_PAY, refDate, nominalPrice),
        realPrice, TOL);
  }

  public void test_realPrice_nominalPrice_settleAfter() {
    double realPrice = 1.055;
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    double nominalPrice = PRICER.nominalPriceFromRealPrice(PRODUCT, RATES_PROVIDER, refDate, realPrice);
    RateObservation obs = RATE_CALC.createRateObservation(VALUATION, START_INDEX);
    double refRate = RateObservationFn.instance().rate(obs, null, null, RATES_PROVIDER);
    double expected = realPrice * (refRate + 1d);
    assertEquals(nominalPrice, expected, TOL);
    assertEquals(PRICER.realPriceFromNominalPrice(PRODUCT, RATES_PROVIDER, refDate, nominalPrice), realPrice, TOL);
  }

  public void test_cleanNominalPrice_dirtyNominalPrice() {
    double dirtyNominalPrice = 1.055;
    LocalDate refDate = LocalDate.of(2014, 6, 10);
    double cleanNominalPrice =
        PRICER.cleanNominalPriceFromDirtyNominalPrice(PRODUCT, RATES_PROVIDER, refDate, dirtyNominalPrice);
    RateObservation obs = RATE_CALC.createRateObservation(VALUATION, START_INDEX);
    double refRate = RateObservationFn.instance().rate(obs, null, null, RATES_PROVIDER);
    double expected = dirtyNominalPrice - PRICER.accruedInterest(PRODUCT, refDate) * (refRate + 1d) / NOTIONAL;
    assertEquals(cleanNominalPrice, expected, TOL);
    assertEquals(PRICER.dirtyNominalPriceFromCleanNominalPrice(PRODUCT, RATES_PROVIDER, refDate, cleanNominalPrice),
        dirtyNominalPrice, TOL);
  }

  //-------------------------------------------------------------------------
  // computes sensitivity with finite difference approximation
  private CurveCurrencyParameterSensitivities fdPvSensitivity(
      CapitalIndexedBond product,
      ImmutableRatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider) {

    CurveCurrencyParameterSensitivities sensi1 =
        FD_CAL.sensitivity(issuerRatesProvider, p -> PRICER.presentValue(product, ratesProvider, p));
    CurveCurrencyParameterSensitivities sensi2 =
        FD_CAL.sensitivity(ratesProvider, p -> PRICER.presentValue(product, p, issuerRatesProvider));
    return sensi1.combinedWith(sensi2);
  }

  // computes sensitivity with finite difference approximation
  private CurveCurrencyParameterSensitivities fdPvSensitivityWithZSpread(
      CapitalIndexedBond product,
      ImmutableRatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CurveCurrencyParameterSensitivities sensi1 = FD_CAL.sensitivity(
        issuerRatesProvider,
        p -> PRICER.presentValueWithZSpread(product, ratesProvider, p, zSpread, compoundedRateType, periodsPerYear));
    CurveCurrencyParameterSensitivities sensi2 = FD_CAL.sensitivity(
        ratesProvider,
        p -> PRICER.presentValueWithZSpread(product, p, issuerRatesProvider, zSpread, compoundedRateType,
            periodsPerYear));
    return sensi1.combinedWith(sensi2);
  }

  // computes sensitivity with finite difference approximation
  private CurveCurrencyParameterSensitivities fdPriceSensitivity(
      Security<CapitalIndexedBond> security,
      ImmutableRatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider) {

    CurveCurrencyParameterSensitivities sensi1 = FD_CAL.sensitivity(
        issuerRatesProvider,
        p -> CurrencyAmount.of(USD, PRICER.dirtyNominalPriceFromCurves(security, ratesProvider, p)));
    CurveCurrencyParameterSensitivities sensi2 = FD_CAL.sensitivity(
        ratesProvider,
        p -> CurrencyAmount.of(USD, PRICER.dirtyNominalPriceFromCurves(security, p, issuerRatesProvider)));
    return sensi1.combinedWith(sensi2);
  }

  // computes sensitivity with finite difference approximation
  private CurveCurrencyParameterSensitivities fdPriceSensitivityWithZSpread(
      Security<CapitalIndexedBond> security,
      ImmutableRatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CurveCurrencyParameterSensitivities sensi1 = FD_CAL.sensitivity(issuerRatesProvider,
        p -> CurrencyAmount.of(USD, PRICER.dirtyNominalPriceFromCurvesWithZSpread(
                security, ratesProvider, p, zSpread, compoundedRateType, periodsPerYear)));
    CurveCurrencyParameterSensitivities sensi2 = FD_CAL.sensitivity(ratesProvider,
        p -> CurrencyAmount.of(USD, PRICER.dirtyNominalPriceFromCurvesWithZSpread(
                security, p, issuerRatesProvider, zSpread, compoundedRateType, periodsPerYear)));
    return sensi1.combinedWith(sensi2);
  }
}
