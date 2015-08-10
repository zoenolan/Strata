/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.e2e;

import static com.opengamma.strata.basics.PayReceive.PAY;
import static com.opengamma.strata.basics.PayReceive.RECEIVE;
import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendars.SAT_SUN;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Map.Entry;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.instrument.index.IndexPrice;
import com.opengamma.analytics.financial.instrument.index.IndexPriceMaster;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsInflationGBP;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsInflationUSD;
import com.opengamma.analytics.financial.interestrate.datasets.StandardTimeSeriesInflationDataSets;
import com.opengamma.analytics.financial.model.interestrate.curve.PriceIndexCurveSimple;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.inflation.InflationProviderDiscount;
import com.opengamma.analytics.math.curve.InterpolatedDoublesCurve;
import com.opengamma.analytics.math.interpolation.FlatExtrapolator1D;
import com.opengamma.analytics.math.interpolation.LinearInterpolator1D;
import com.opengamma.analytics.util.time.DateUtils;
import com.opengamma.analytics.util.timeseries.zdt.ZonedDateTimeDoubleEntryIterator;
import com.opengamma.analytics.util.timeseries.zdt.ZonedDateTimeDoubleTimeSeries;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.index.PriceIndices;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.rate.swap.CompoundingMethod;
import com.opengamma.strata.finance.rate.swap.FixedRateCalculation;
import com.opengamma.strata.finance.rate.swap.InflationRateCalculation;
import com.opengamma.strata.finance.rate.swap.NotionalSchedule;
import com.opengamma.strata.finance.rate.swap.PaymentSchedule;
import com.opengamma.strata.finance.rate.swap.RateCalculationSwapLeg;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.value.ForwardPriceIndexValues;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.pricer.impl.Legacy;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

/**
 * Test end to end.
 */
@Test
public class SwapInflationEnd2EndTest {
  private static final ZonedDateTime CALIBRATION_DATE = DateUtils.getUTCDate(2014, 10, 9);
  private static final LocalDate VALUATION_DATE = CALIBRATION_DATE.toLocalDate();
  private static final Pair<InflationProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_INFL_1_PAIR = 
      StandardDataSetsInflationUSD.getCurvesUsdOisUsCpi(CALIBRATION_DATE);
  private static final InflationProviderDiscount MULTICURVE_INFL_1 = MULTICURVE_INFL_1_PAIR.getFirst();
  private static final PriceIndex US_CPI = PriceIndices.US_CPI_U;
  private static final ZonedDateTimeDoubleTimeSeries HTS_CPI_OLD =
      StandardTimeSeriesInflationDataSets.timeSeriesUsCpi(CALIBRATION_DATE);
  // historical time series of price index
  private static final LocalDateDoubleTimeSeries HTS_CPI;
  static {
    LocalDateDoubleTimeSeriesBuilder builder = LocalDateDoubleTimeSeries.builder();
    ZonedDateTimeDoubleEntryIterator itr = HTS_CPI_OLD.iterator();
    while (itr.hasNext()) {
      Entry<ZonedDateTime, Double> entry = itr.next();
      builder.put(entry.getKey().toLocalDate(), entry.getValue());
    }
    HTS_CPI = builder.build();
  }
  // price index curve
  private static final InterpolatedNodalCurve CURVE;
  static {
    IndexPrice indexPrice = IndexPriceMaster.getInstance().getIndex("USCPI");
    PriceIndexCurveSimple indexCurve = (PriceIndexCurveSimple) MULTICURVE_INFL_1
        .getPriceIndexCurves().get(indexPrice);
    InterpolatedDoublesCurve curve = (InterpolatedDoublesCurve) indexCurve.getCurve();
    DefaultCurveMetadata metaData = DefaultCurveMetadata.builder()
        .curveName(CurveName.of(curve.getName()))
        .xValueType(ValueType.MONTHS)
        .yValueType(ValueType.PRICE_INDEX)
        .build();
    int nData = curve.getXDataAsPrimitive().length;
    double[] xValueInt = new double[nData];
    for (int i = 0; i < nData; ++i) {
      xValueInt[i] = (int) (curve.getXDataAsPrimitive()[i] * 12d);
    }
    CURVE = InterpolatedNodalCurve.builder()
        .extrapolatorLeft(new FlatExtrapolator1D())
        .extrapolatorRight(new FlatExtrapolator1D())
        .interpolator(new LinearInterpolator1D())
        .metadata(metaData)
        .xValues(xValueInt)
        .yValues(curve.getYDataAsPrimitive())
        .build();
  }
  // create rates provider
  private static final RatesProvider RATES_PROVIDER = ImmutableRatesProvider.builder()
      .valuationDate(VALUATION_DATE)
      .fxMatrix(MULTICURVE_INFL_1.getFxRates())
      .discountCurves(Legacy.discountCurves(MULTICURVE_INFL_1.getMulticurveProvider()))
      .indexCurves(Legacy.indexCurves(MULTICURVE_INFL_1.getMulticurveProvider()))
      .priceIndexValues(
          ImmutableMap.of(
              US_CPI,
              ForwardPriceIndexValues.of(US_CPI, YearMonth.from(VALUATION_DATE), HTS_CPI, CURVE)))
      .timeSeries(ImmutableMap.of(USD_FED_FUND, LocalDateDoubleTimeSeries.empty()))
      .build();
  
  // create node swap
  private static final double NOTIONAL = 10_000_000;
  private static final LocalDate START_DATE_1 = LocalDate.of(2014, 10, 11);
  private static final LocalDate END_DATE_1 = LocalDate.of(2016, 10, 11);
  private static final Frequency FREQUENCY_1 = Frequency.ofYears(2);
  private static final RateCalculationSwapLeg INFLATION_INTERPOLATED_SWAP_LEG_REC_1 = RateCalculationSwapLeg.builder()
      .payReceive(RECEIVE)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_1)
          .endDate(END_DATE_1)
          .frequency(FREQUENCY_1)
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_1)
          .paymentDateOffset(DaysAdjustment.NONE)
          .build())
      .calculation(InflationRateCalculation.builder()
          .index(US_CPI)
          .interpolated(true)
          .lag(Period.ofMonths(3))
          .build())
      .notionalSchedule(NotionalSchedule.of(USD, NOTIONAL))
      .build();
  private static final RateCalculationSwapLeg INFLATION_FIXED_SWAP_LEG_PAY_1 = RateCalculationSwapLeg.builder()
      .payReceive(PAY)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_1)
          .endDate(END_DATE_1)
          .frequency(Frequency.ofYears(1))
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_1)
          .paymentDateOffset(DaysAdjustment.NONE)
          .compoundingMethod(CompoundingMethod.STRAIGHT)
          .build())
      .notionalSchedule(NotionalSchedule.of(USD, NOTIONAL))
      .calculation(FixedRateCalculation.builder()
          .rate(ValueSchedule.of(0.02d))
          .dayCount(DayCounts.ONE_ONE)
          .build())
      .build();
  private static final Swap SWAP_INFLATION_1 = Swap.builder()
      .legs(INFLATION_INTERPOLATED_SWAP_LEG_REC_1, INFLATION_FIXED_SWAP_LEG_PAY_1)
      .build();
  
  // create aged swap
  private static final LocalDate START_DATE_2 = LocalDate.of(2014, 1, 10);
  private static final LocalDate END_DATE_2 = LocalDate.of(2019, 1, 10);
  private static final Frequency FREQUENCY_2 = Frequency.ofYears(5);
  private static final RateCalculationSwapLeg INFLATION_INTERPOLATED_SWAP_LEG_REC_2 = RateCalculationSwapLeg.builder()
      .payReceive(RECEIVE)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_2)
          .endDate(END_DATE_2)
          .frequency(FREQUENCY_2)
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_2)
          .paymentDateOffset(DaysAdjustment.NONE)
          .build())
      .calculation(InflationRateCalculation.builder()
          .index(US_CPI)
          .interpolated(true)
          .lag(Period.ofMonths(3))
          .build())
      .notionalSchedule(NotionalSchedule.of(USD, NOTIONAL))
      .build();
  private static final RateCalculationSwapLeg INFLATION_FIXED_SWAP_LEG_PAY_2 = RateCalculationSwapLeg.builder()
      .payReceive(PAY)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_2)
          .endDate(END_DATE_2)
          .frequency(Frequency.ofYears(1))
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_2)
          .paymentDateOffset(DaysAdjustment.NONE)
          .compoundingMethod(CompoundingMethod.STRAIGHT)
          .build())
      .notionalSchedule(NotionalSchedule.of(USD, NOTIONAL))
      .calculation(FixedRateCalculation.builder()
          .rate(ValueSchedule.of(0.01d))
          .dayCount(DayCounts.ONE_ONE)
          .build())
      .build();
  private static final Swap SWAP_INFLATION_2 = Swap.builder()
      .legs(INFLATION_INTERPOLATED_SWAP_LEG_REC_2, INFLATION_FIXED_SWAP_LEG_PAY_2)
      .build();

  private static final DiscountingSwapProductPricer PRICER = DiscountingSwapProductPricer.DEFAULT;
  private static final double TOL = 1.0e-4; // large tolerance due to interpolation difference in price index curve.

  public void test_nodeSwap2Y() {
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_1, RATES_PROVIDER);
    assertEquals(pv.getAmount(USD).getAmount(), 0d, NOTIONAL * TOL);
  }

  public void test_agedSwap5Y() {
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_2, RATES_PROVIDER);
    assertEquals(pv.getAmount(USD).getAmount(), 557423.3816632524, NOTIONAL * TOL);
  }


  private static final PriceIndex GBP_RPI = PriceIndices.GB_RPI;

  private static final ZonedDateTime STD_REFERENCE_DATE = DateUtils.getUTCDate(2014, 4, 11);
  private static final Pair<InflationProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_PAIR_STD = StandardDataSetsInflationGBP
      .getCurvesGBPRpiAndSonia();
  private static final InflationProviderDiscount INFLATION_MULTICURVE_STD = MULTICURVE_PAIR_STD.getFirst();
  
  private static final LocalDateDoubleTimeSeries TS_PRICE_INDEX_USD_WITH_TODAY = LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2013, 12, 31), 253.4)
      .put(LocalDate.of(2013, 12, 31), 253.4)
      .put(LocalDate.of(2013, 12, 31), 253.4)
      .build();

  // price index curve
  private static final InterpolatedNodalCurve CURVE_GB;
  static {
    IndexPrice[] indexList = StandardDataSetsInflationGBP.indexONArray();
    IndexPrice gbpRpi = indexList[0];
    PriceIndexCurveSimple indexCurve = (PriceIndexCurveSimple) INFLATION_MULTICURVE_STD
        .getPriceIndexCurves().get(gbpRpi);
    InterpolatedDoublesCurve curve = (InterpolatedDoublesCurve) indexCurve.getCurve();
    DefaultCurveMetadata metaData = DefaultCurveMetadata.builder()
        .curveName(CurveName.of(curve.getName()))
        .xValueType(ValueType.MONTHS)
        .yValueType(ValueType.PRICE_INDEX)
        .build();
    int nData = curve.getXDataAsPrimitive().length;
    double[] xValueInt = new double[nData];
    for (int i = 0; i < nData; ++i) {
      //      System.out.println(curve.getXDataAsPrimitive()[i] + "\t" + (curve.getXDataAsPrimitive()[i] * 12d));
      xValueInt[i] = (int) (curve.getXDataAsPrimitive()[i] * 12d);
    }
    CURVE_GB = InterpolatedNodalCurve.builder()
        .extrapolatorLeft(new FlatExtrapolator1D())
        .extrapolatorRight(new FlatExtrapolator1D())
        .interpolator(new LinearInterpolator1D())
        .metadata(metaData)
        .xValues(xValueInt)
        .yValues(curve.getYDataAsPrimitive())
        .build();
  }
  // create rates provider
  private static final RatesProvider RATES_PROVIDER_GB = ImmutableRatesProvider.builder()
      .valuationDate(STD_REFERENCE_DATE.toLocalDate())
      .fxMatrix(INFLATION_MULTICURVE_STD.getFxRates())
      .discountCurves(Legacy.discountCurves(INFLATION_MULTICURVE_STD.getMulticurveProvider()))
      .indexCurves(Legacy.indexCurves(INFLATION_MULTICURVE_STD.getMulticurveProvider()))
      .priceIndexValues(
          ImmutableMap.of(
              GBP_RPI,
              ForwardPriceIndexValues.of(GBP_RPI, YearMonth.from(STD_REFERENCE_DATE), TS_PRICE_INDEX_USD_WITH_TODAY,
                  CURVE_GB)))
      .timeSeries(ImmutableMap.of(GBP_SONIA, LocalDateDoubleTimeSeries.empty()))
      .build();

  // GBP inflation swap, monthly
  private static final LocalDate START_DATE_3 = LocalDate.of(2014, 4, 4);
  private static final LocalDate END_DATE_3 = LocalDate.of(2019, 4, 4);
  private static final Frequency FREQUENCY_3 = Frequency.ofYears(5);
  private static final RateCalculationSwapLeg INFLATION_MONTHLY_SWAP_LEG_REC_3 = RateCalculationSwapLeg.builder()
      .payReceive(RECEIVE)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_3)
          .endDate(END_DATE_3)
          .frequency(FREQUENCY_3)
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_3)
          .paymentDateOffset(DaysAdjustment.NONE)
          .build())
      .calculation(InflationRateCalculation.builder()
          .index(GBP_RPI)
          .interpolated(false)
          .lag(Period.ofMonths(3))
          .build())
      .notionalSchedule(NotionalSchedule.of(GBP, NOTIONAL))
      .build();
  private static final RateCalculationSwapLeg INFLATION_FIXED_SWAP_LEG_PAY_3 = RateCalculationSwapLeg.builder()
      .payReceive(PAY)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(START_DATE_3)
          .endDate(END_DATE_3)
          .frequency(Frequency.ofYears(1))
          .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, SAT_SUN))
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(FREQUENCY_3)
          .paymentDateOffset(DaysAdjustment.NONE)
          .compoundingMethod(CompoundingMethod.STRAIGHT)
          .build())
      .notionalSchedule(NotionalSchedule.of(GBP, NOTIONAL))
      .calculation(FixedRateCalculation.builder()
          .rate(ValueSchedule.of(0.02506))
          .dayCount(DayCounts.ONE_ONE)
          .build())
      .build();
  private static final Swap SWAP_INFLATION_3 = Swap.builder()
      .legs(INFLATION_MONTHLY_SWAP_LEG_REC_3, INFLATION_FIXED_SWAP_LEG_PAY_3)
      .build();

  DiscountingSwapLegPricer pricer = DiscountingSwapLegPricer.DEFAULT;

  @Test(enabled = false)
  // TODO investigate this
  public void test() {
    System.out.println(pricer.presentValue(INFLATION_FIXED_SWAP_LEG_PAY_3, RATES_PROVIDER_GB));
    System.out.println(pricer.presentValue(INFLATION_MONTHLY_SWAP_LEG_REC_3, RATES_PROVIDER_GB));
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_3, RATES_PROVIDER_GB);
    System.out.println(pv);
  }
}
