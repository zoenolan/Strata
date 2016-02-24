/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.bond;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.explain.ExplainKey;
import com.opengamma.strata.market.explain.ExplainMapBuilder;
import com.opengamma.strata.market.sensitivity.IssuerCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.CompoundedRateType;
import com.opengamma.strata.market.view.IssuerCurveDiscountFactors;
import com.opengamma.strata.pricer.rate.RateObservationFn;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.bond.CapitalIndexedBondPaymentPeriod;
import com.opengamma.strata.product.rate.RateObservation;

public class DiscountingCapitalIndexedBondPaymentPeriodPricer {

  /**
   * Default implementation. 
   */
  public static final DiscountingCapitalIndexedBondPaymentPeriodPricer DEFAULT =
      new DiscountingCapitalIndexedBondPaymentPeriodPricer(RateObservationFn.instance());

  /**
   * Rate observation.
   */
  private final RateObservationFn<RateObservation> rateObservationFn;

  public DiscountingCapitalIndexedBondPaymentPeriodPricer(RateObservationFn<RateObservation> rateObservationFn) {
    this.rateObservationFn = ArgChecker.notNull(rateObservationFn, "rateObservationFn");
  }

  public RateObservationFn<RateObservation> getRateObservationFn() {
    return rateObservationFn;
  }

  //-------------------------------------------------------------------------
  public double presentValue(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      IssuerCurveDiscountFactors issuerDiscountFactors) {

    double df = issuerDiscountFactors.discountFactor(period.getPaymentDate());
    return df * forecastValue(period, ratesProvider);
  }

  public double presentValueWithZSpread(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      IssuerCurveDiscountFactors issuerDiscountFactors,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    double df = issuerDiscountFactors.getDiscountFactors()
        .discountFactorWithSpread(period.getPaymentDate(), zSpread, compoundedRateType, periodsPerYear);
    return df * forecastValue(period, ratesProvider);
  }

  public double forecastValue(CapitalIndexedBondPaymentPeriod period, RatesProvider ratesProvider) {
    if (period.getPaymentDate().isBefore(ratesProvider.getValuationDate())) {
      return 0d;
    }
    double rate = rateObservationFn.rate(
        period.getRateObservation(), period.getStartDate(), period.getEndDate(), ratesProvider);
    return period.getNotional() * period.getRealCoupon() * rate;
  }

  //-------------------------------------------------------------------------
  public PointSensitivityBuilder presentValueSensitivity(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      IssuerCurveDiscountFactors issuerDiscountFactors) {

    if (period.getPaymentDate().isBefore(ratesProvider.getValuationDate())) {
      return PointSensitivityBuilder.none(); 
    }
    double rate = rateObservationFn.rate(
        period.getRateObservation(), period.getStartDate(), period.getEndDate(), ratesProvider);
    PointSensitivityBuilder rateSensi = rateObservationFn.rateSensitivity(
        period.getRateObservation(), period.getStartDate(), period.getEndDate(), ratesProvider);
    double df = issuerDiscountFactors.discountFactor(period.getPaymentDate());
    PointSensitivityBuilder dfSensi = issuerDiscountFactors.zeroRatePointSensitivity(period.getPaymentDate());
    double factor = period.getNotional() * period.getRealCoupon();
    return rateSensi.multipliedBy(df * factor).combinedWith(dfSensi.multipliedBy(rate * factor));
  }

  public PointSensitivityBuilder presentValueSensitivityWithZSpread(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      IssuerCurveDiscountFactors issuerDiscountFactors,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    if (period.getPaymentDate().isBefore(ratesProvider.getValuationDate())) {
      return PointSensitivityBuilder.none();
    }
    double rate = rateObservationFn.rate(
        period.getRateObservation(), period.getStartDate(), period.getEndDate(), ratesProvider);
    PointSensitivityBuilder rateSensi = rateObservationFn.rateSensitivity(
        period.getRateObservation(), period.getStartDate(), period.getEndDate(), ratesProvider);
    double df = issuerDiscountFactors.getDiscountFactors()
        .discountFactorWithSpread(period.getPaymentDate(), zSpread, compoundedRateType, periodsPerYear);
    ZeroRateSensitivity zeroSensi = issuerDiscountFactors.getDiscountFactors()
        .zeroRatePointSensitivityWithSpread(period.getPaymentDate(), zSpread, compoundedRateType, periodsPerYear);
    IssuerCurveZeroRateSensitivity dfSensi =
        IssuerCurveZeroRateSensitivity.of(zeroSensi, issuerDiscountFactors.getLegalEntityGroup());
    double factor = period.getNotional() * period.getRealCoupon();
    return rateSensi.multipliedBy(df * factor).combinedWith(dfSensi.multipliedBy(rate * factor));
  }

  //-------------------------------------------------------------------------
  public void explainPresentValue(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      ExplainMapBuilder builder,
      IssuerCurveDiscountFactors issuerDiscountFactors) {

    Currency currency = period.getCurrency();
    LocalDate paymentDate = period.getPaymentDate();
    builder.put(ExplainKey.ENTRY_TYPE, "CapitalIndexedBondPaymentPeriod");
    builder.put(ExplainKey.PAYMENT_DATE, paymentDate);
    builder.put(ExplainKey.PAYMENT_CURRENCY, currency);
    builder.put(ExplainKey.START_DATE, period.getStartDate());
    builder.put(ExplainKey.UNADJUSTED_START_DATE, period.getUnadjustedStartDate());
    builder.put(ExplainKey.END_DATE, period.getEndDate());
    builder.put(ExplainKey.ACCRUAL_DAYS, (int) DAYS.between(period.getStartDate(), period.getEndDate()));
    builder.put(ExplainKey.UNADJUSTED_END_DATE, period.getUnadjustedEndDate());
    if (paymentDate.isBefore(ratesProvider.getValuationDate())) {
      builder.put(ExplainKey.FORECAST_VALUE, CurrencyAmount.zero(currency));
      builder.put(ExplainKey.PRESENT_VALUE, CurrencyAmount.zero(currency));
    } else {
      builder.put(ExplainKey.DISCOUNT_FACTOR, ratesProvider.discountFactor(currency, paymentDate));
      builder.put(ExplainKey.FORECAST_VALUE, CurrencyAmount.of(currency, forecastValue(period, ratesProvider)));
      builder.put(ExplainKey.PRESENT_VALUE,
          CurrencyAmount.of(currency, presentValue(period, ratesProvider, issuerDiscountFactors)));
    }
  }

  public void explainPresentValueWithSpread(
      CapitalIndexedBondPaymentPeriod period,
      RatesProvider ratesProvider,
      ExplainMapBuilder builder,
      IssuerCurveDiscountFactors issuerDiscountFactors,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    Currency currency = period.getCurrency();
    LocalDate paymentDate = period.getPaymentDate();
    builder.put(ExplainKey.ENTRY_TYPE, "CapitalIndexedBondPaymentPeriod");
    builder.put(ExplainKey.PAYMENT_DATE, paymentDate);
    builder.put(ExplainKey.PAYMENT_CURRENCY, currency);
    builder.put(ExplainKey.START_DATE, period.getStartDate());
    builder.put(ExplainKey.UNADJUSTED_START_DATE, period.getUnadjustedStartDate());
    builder.put(ExplainKey.END_DATE, period.getEndDate());
    builder.put(ExplainKey.ACCRUAL_DAYS, (int) DAYS.between(period.getStartDate(), period.getEndDate()));
    builder.put(ExplainKey.UNADJUSTED_END_DATE, period.getUnadjustedEndDate());
    if (paymentDate.isBefore(ratesProvider.getValuationDate())) {
      builder.put(ExplainKey.FORECAST_VALUE, CurrencyAmount.zero(currency));
      builder.put(ExplainKey.PRESENT_VALUE, CurrencyAmount.zero(currency));
    } else {
      builder.put(ExplainKey.DISCOUNT_FACTOR, ratesProvider.discountFactor(currency, paymentDate));
      builder.put(ExplainKey.FORECAST_VALUE, CurrencyAmount.of(currency, forecastValue(period, ratesProvider)));
      builder.put(ExplainKey.PRESENT_VALUE, CurrencyAmount.of(currency, presentValueWithZSpread(
          period, ratesProvider, issuerDiscountFactors, zSpread, compoundedRateType, periodsPerYear)));
    }
  }

}
