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
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.date.HolidayCalendars.SAT_SUN;
import static com.opengamma.strata.basics.index.PriceIndices.GB_RPI;
import static com.opengamma.strata.basics.index.PriceIndices.US_CPI_U;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.finance.rate.swap.CompoundingMethod;
import com.opengamma.strata.finance.rate.swap.FixedRateCalculation;
import com.opengamma.strata.finance.rate.swap.InflationRateCalculation;
import com.opengamma.strata.finance.rate.swap.NotionalSchedule;
import com.opengamma.strata.finance.rate.swap.PaymentSchedule;
import com.opengamma.strata.finance.rate.swap.RateCalculationSwapLeg;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.value.ForwardPriceIndexValues;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.math.impl.interpolation.Interpolator1DFactory;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

/**
 * Test end to end.
 */
@Test
public class SwapInflationEnd2EndTest {
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;

  private static final LocalDate VALUATION_DATE_USD = LocalDate.of(2014, 10, 9);
  // discount curve, USD
  private static final DoubleArray DSC_TIME_USD = DoubleArray.copyOf(new double[] {0.0027397260273972603,
    0.0136986301369863, 0.1095890410958904, 0.18904109589041096, 0.27123287671232876, 0.5178082191780822,
    0.7671232876712328, 1.0191780821917809, 2.025218953514485, 3.0246575342465754, 4.021917808219178,
    5.019178082191781, 6.019754472640168, 7.024657534246575, 8.024657534246575, 9.024657534246575, 10.019754472640168 });
  private static final DoubleArray DSC_RATE_USD = DoubleArray.copyOf(new double[] {0.0016222186172986138,
    0.001622209965572477, 7.547616096755544E-4, 9.003947315389025E-4, 9.833562990057003E-4, 9.300905368344651E-4,
    0.0010774349342544426, 0.001209299356175582, 0.003243498783874946, 0.007148138535707508, 0.011417234937364525,
    0.015484713638367467, 0.01894872475170524, 0.02177798040124286, 0.024146976832379798, 0.02610320121432829,
    0.027814843351943817 });
  private static final CurveName DSC_NAME_USD = CurveName.of("USD-DSC");
  private static final CurveMetadata DSC_META_USD = Curves.zeroRates(DSC_NAME_USD, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve DSC_CURVE_USD =
      InterpolatedNodalCurve.of(DSC_META_USD, DSC_TIME_USD, DSC_RATE_USD, INTERPOLATOR);
  // index curve, USD
  private static final DoubleArray INDEX_TIME_USD = DoubleArray.copyOf(new double[] {0.893150684931507,
    1.894071412530878, 2.893150684931507, 3.893150684931507, 4.893150684931507, 5.894071412530878, 6.893150684931507,
    7.893150684931507, 8.893150684931507, 9.894071412530877, 11.893150684931507, 14.893150684931507,
    19.893150684931506, 24.893150684931506, 29.894071412530877 });
  private static final DoubleArray INDEX_VALUE_USD = DoubleArray.copyOf(new double[] {242.88404516129032,
    248.03712245417105, 252.98128118335094, 258.0416354687366, 263.20242369585515, 268.4653023378886,
    273.83617795725064, 279.3124974961296, 284.8987721100803, 290.5954768446179, 302.3336095056465, 320.8351638061777,
    354.2203489141063, 391.08797576744865, 431.7913437911175 });
  private static final CurveName INDEX_NAME_USD = CurveName.of("US-ZCHICP");
  private static final DefaultCurveMetadata INDEX_META_USD = DefaultCurveMetadata.builder()
      .curveName(INDEX_NAME_USD)
      .xValueType(ValueType.MONTHS)
      .yValueType(ValueType.PRICE_INDEX)
      .build();
  private static final double[] INDEX_MONTH_USD;
  static {
    INDEX_MONTH_USD = new double[INDEX_TIME_USD.size()];
    for (int i = 0; i < INDEX_TIME_USD.size(); ++i) {
      INDEX_MONTH_USD[i] = (int) (INDEX_TIME_USD.get(i) * 12d - 0.1 / 12d);
    }
  }
  private static final InterpolatedNodalCurve INDEX_CURVE_USD = InterpolatedNodalCurve.of(INDEX_META_USD,
      DoubleArray.copyOf(INDEX_MONTH_USD), INDEX_VALUE_USD, INTERPOLATOR);
  // time series, USD
  private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_DATE;
  private static final LocalDateDoubleTimeSeries INDEX_SERIES_USD;
  static {
    LocalDate[] times = new LocalDate[] {LocalDate.parse("2005-01-31", FMT), LocalDate.parse("2005-02-28", FMT),
      LocalDate.parse("2005-03-31", FMT), LocalDate.parse("2005-04-30", FMT), LocalDate.parse("2005-05-31", FMT),
      LocalDate.parse("2005-06-30", FMT), LocalDate.parse("2005-07-31", FMT), LocalDate.parse("2005-08-31", FMT),
      LocalDate.parse("2005-09-30", FMT), LocalDate.parse("2005-10-31", FMT), LocalDate.parse("2005-11-30", FMT),
      LocalDate.parse("2005-12-31", FMT), LocalDate.parse("2006-01-31", FMT), LocalDate.parse("2006-02-28", FMT),
      LocalDate.parse("2006-03-31", FMT), LocalDate.parse("2006-04-30", FMT), LocalDate.parse("2006-05-31", FMT),
      LocalDate.parse("2006-06-30", FMT), LocalDate.parse("2006-07-31", FMT), LocalDate.parse("2006-08-31", FMT),
      LocalDate.parse("2006-09-30", FMT), LocalDate.parse("2006-10-31", FMT), LocalDate.parse("2006-11-30", FMT),
      LocalDate.parse("2006-12-31", FMT), LocalDate.parse("2007-01-31", FMT), LocalDate.parse("2007-02-28", FMT),
      LocalDate.parse("2007-03-31", FMT), LocalDate.parse("2007-04-30", FMT), LocalDate.parse("2007-05-31", FMT),
      LocalDate.parse("2007-06-30", FMT), LocalDate.parse("2007-07-31", FMT), LocalDate.parse("2007-08-31", FMT),
      LocalDate.parse("2007-09-30", FMT), LocalDate.parse("2007-10-31", FMT), LocalDate.parse("2007-11-30", FMT),
      LocalDate.parse("2007-12-31", FMT), LocalDate.parse("2008-01-31", FMT), LocalDate.parse("2008-02-29", FMT),
      LocalDate.parse("2008-03-31", FMT), LocalDate.parse("2008-04-30", FMT), LocalDate.parse("2008-05-31", FMT),
      LocalDate.parse("2008-06-30", FMT), LocalDate.parse("2008-07-31", FMT), LocalDate.parse("2008-08-31", FMT),
      LocalDate.parse("2008-09-30", FMT), LocalDate.parse("2008-10-31", FMT), LocalDate.parse("2008-11-30", FMT),
      LocalDate.parse("2008-12-31", FMT), LocalDate.parse("2009-01-31", FMT), LocalDate.parse("2009-02-28", FMT),
      LocalDate.parse("2009-03-31", FMT), LocalDate.parse("2009-04-30", FMT), LocalDate.parse("2009-05-31", FMT),
      LocalDate.parse("2009-06-30", FMT), LocalDate.parse("2009-07-31", FMT), LocalDate.parse("2009-08-31", FMT),
      LocalDate.parse("2009-09-30", FMT), LocalDate.parse("2009-10-31", FMT), LocalDate.parse("2009-11-30", FMT),
      LocalDate.parse("2009-12-31", FMT), LocalDate.parse("2010-01-31", FMT), LocalDate.parse("2010-02-28", FMT),
      LocalDate.parse("2010-03-31", FMT), LocalDate.parse("2010-04-30", FMT), LocalDate.parse("2010-05-31", FMT),
      LocalDate.parse("2010-06-30", FMT), LocalDate.parse("2010-07-31", FMT), LocalDate.parse("2010-08-31", FMT),
      LocalDate.parse("2010-09-30", FMT), LocalDate.parse("2010-10-31", FMT), LocalDate.parse("2010-11-30", FMT),
      LocalDate.parse("2010-12-31", FMT), LocalDate.parse("2011-01-31", FMT), LocalDate.parse("2011-02-28", FMT),
      LocalDate.parse("2011-03-31", FMT), LocalDate.parse("2011-04-30", FMT), LocalDate.parse("2011-05-31", FMT),
      LocalDate.parse("2011-06-30", FMT), LocalDate.parse("2011-07-31", FMT), LocalDate.parse("2011-08-31", FMT),
      LocalDate.parse("2011-09-30", FMT), LocalDate.parse("2011-10-31", FMT), LocalDate.parse("2011-11-30", FMT),
      LocalDate.parse("2011-12-31", FMT), LocalDate.parse("2012-01-31", FMT), LocalDate.parse("2012-02-29", FMT),
      LocalDate.parse("2012-03-31", FMT), LocalDate.parse("2012-04-30", FMT), LocalDate.parse("2012-05-31", FMT),
      LocalDate.parse("2012-06-30", FMT), LocalDate.parse("2012-07-31", FMT), LocalDate.parse("2012-08-31", FMT),
      LocalDate.parse("2012-09-30", FMT), LocalDate.parse("2012-10-31", FMT), LocalDate.parse("2012-11-30", FMT),
      LocalDate.parse("2012-12-31", FMT), LocalDate.parse("2013-01-31", FMT), LocalDate.parse("2013-02-28", FMT),
      LocalDate.parse("2013-03-31", FMT), LocalDate.parse("2013-04-30", FMT), LocalDate.parse("2013-05-31", FMT),
      LocalDate.parse("2013-06-30", FMT), LocalDate.parse("2013-07-31", FMT), LocalDate.parse("2013-08-31", FMT),
      LocalDate.parse("2013-09-30", FMT), LocalDate.parse("2013-10-31", FMT), LocalDate.parse("2013-11-30", FMT),
      LocalDate.parse("2013-12-31", FMT), LocalDate.parse("2014-01-31", FMT), LocalDate.parse("2014-02-28", FMT),
      LocalDate.parse("2014-03-31", FMT), LocalDate.parse("2014-04-30", FMT), LocalDate.parse("2014-05-31", FMT),
      LocalDate.parse("2014-06-30", FMT), LocalDate.parse("2014-07-31", FMT), LocalDate.parse("2014-08-31", FMT) };
    double[] values = new double[] {211.143, 212.193, 212.709, 213.24, 213.856, 215.693, 215.351, 215.834, 215.969,
      216.177, 216.33, 215.949, 211.143, 212.193, 212.709, 213.24, 213.856, 215.693, 215.351, 215.834, 215.969,
      216.177, 216.33, 215.949, 211.143, 212.193, 212.709, 213.24, 213.856, 215.693, 215.351, 215.834, 215.969,
      216.177, 216.33, 215.949, 211.143, 212.193, 212.709, 213.24, 213.856, 215.693, 215.351, 215.834, 215.969,
      216.177, 216.33, 215.949, 211.143, 212.193, 212.709, 213.24, 213.856, 215.693, 215.351, 215.834, 215.969,
      216.177, 216.33, 215.949, 216.687, 216.741, 217.631, 218.009, 218.178, 217.965, 218.011, 218.312, 218.439,
      218.711, 218.803, 219.179, 220.223, 221.309, 223.467, 224.906, 225.964, 225.722, 225.922, 226.545, 226.889,
      226.421, 226.23, 225.672, 226.655, 227.663, 229.392, 230.085, 229.815, 229.478, 229.104, 230.379, 231.407,
      231.317, 230.221, 229.601, 230.28, 232.166, 232.773, 232.531, 232.945, 233.504, 233.596, 233.877, 234.149,
      233.546, 233.069, 233.049, 233.916, 234.781, 236.293, 237.072, 237.9, 238.343, 238.25, 237.852 };
    LocalDateDoubleTimeSeriesBuilder builder = LocalDateDoubleTimeSeries.builder();
    for (int i = 0; i < times.length; ++i) {
      builder.put(times[i], values[i]);
    }
    INDEX_SERIES_USD = builder.build();
  }
  private static final PriceIndexValues INDEX_VALUES = ForwardPriceIndexValues.of(US_CPI_U,
      YearMonth.from(VALUATION_DATE_USD), INDEX_SERIES_USD, INDEX_CURVE_USD);

  // create rates provider
  private static final RatesProvider RATES_PROVIDER = ImmutableRatesProvider.builder()
      .valuationDate(VALUATION_DATE_USD)
      .fxMatrix(FxMatrix.empty())
      .discountCurves(ImmutableMap.of(USD, DSC_CURVE_USD))
      .priceIndexValues(ImmutableMap.of(US_CPI_U, INDEX_VALUES))
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
          .index(US_CPI_U)
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
          .index(US_CPI_U)
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
  private static final double TOL = 1.0e-4; // large tolerance due to difference in price index curve construction.

  public void test_nodeSwap2Y() {
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_1, RATES_PROVIDER);
    assertEquals(pv.getAmount(USD).getAmount(), 0d, NOTIONAL * TOL);
  }

  public void test_agedSwap5Y() {
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_2, RATES_PROVIDER);
    assertEquals(pv.getAmount(USD).getAmount(), 557423.3816632524, NOTIONAL * TOL);
  }

  private static final LocalDate VALUATION_DATE_GBP = LocalDate.of(2014, 4, 11);
  // discount curve, GBP
  private static final DoubleArray DSC_TIME_GBP = DoubleArray.copyOf(new double[] {0.00821917808219178,
    0.010958904109589041, 0.08767123287671233, 0.16986301369863013, 0.25753424657534246, 0.5095890410958904,
    0.7589041095890411, 1.0082191780821919, 2.004715921850438, 3.0027397260273974, 4.002739726027397,
    5.002739726027397, 6.012912643161913, 7.005479452054795, 8.002739726027396, 9.002739726027396, 10.004715921850437,
    12.008219178082191, 15.002739726027396, 20.002739726027396, 30.004715921850437 });
  private static final DoubleArray DSC_RATE_GBP = DoubleArray.copyOf(new double[] {0.004224926642958077,
    0.004222438897931736, 0.0042398588936040996, 0.004217776521030551, 0.0042243958568607696, 0.004299126741134502,
    0.0045040811553855534, 0.004892234137920406, 0.007659374525041726, 0.010980878586007237, 0.013683742816963869,
    0.01588237435416976, 0.017774967597837126, 0.019399132160300248, 0.020808812487819656, 0.022097282211715332,
    0.02323886149685708, 0.025222992643602, 0.027342868679069075, 0.029418385828696377, 0.030682245979066464 });
  private static final CurveName DSC_NAME_GBP = CurveName.of("GBP-DSC");
  private static final CurveMetadata DSC_META_GBP = Curves.zeroRates(DSC_NAME_GBP, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve DSC_CURVE_GBP =
      InterpolatedNodalCurve.of(DSC_META_GBP, DSC_TIME_GBP, DSC_RATE_GBP, INTERPOLATOR);
  // index curve, GBP
  private static final DoubleArray INDEX_TIME_GBP = DoubleArray.copyOf(new double[] {2.012912643161913,
    3.0191780821917806, 4.013698630136986, 5.010958904109589, 6.012912643161913, 7.010958904109589, 8.021917808219179,
    9.016438356164384, 10.012912643161913, 12.01095890410959, 15.013698630136986, 20.016438356164382,
    25.01095890410959, 30.023841604910547 });
  private static final DoubleArray INDEX_VALUE_GBP = DoubleArray.copyOf(new double[] {263.64565665294003,
    271.85265899103206, 278.87253484294587, 286.9128294197508, 294.9622659713281, 303.70235859901453,
    312.95879177623215, 323.1164667816258, 323.0126907068947, 357.11632418991593, 395.6649868993474, 441.5955425459237,
    556.4807348948038, 652.3926312415971 });
  //  private static final DoubleArray INDEX_VALUE_GBP = DoubleArray.copyOf(new double[] {263.64565665294,
  //    270.1320988365087, 277.42214727023634, 285.2782640387521, 293.31600762735263, 301.92692588198935,
  //    311.00214736478756, 320.9897910229209, 323.03403153762366, 353.65586311650344, 393.0270765862022,
  //    439.68386553106046, 551.8172657594129, 648.2628129708963 }); // nearest 
  private static final CurveName INDEX_NAME_GBP = CurveName.of("GB-RPI");
  private static final DefaultCurveMetadata INDEX_META_GBP = DefaultCurveMetadata.builder()
      .curveName(INDEX_NAME_GBP)
      .xValueType(ValueType.MONTHS)
      .yValueType(ValueType.PRICE_INDEX)
      .build();
  private static final double[] INDEX_MONTH_GBP;
  static {
    INDEX_MONTH_GBP = new double[INDEX_TIME_GBP.size()];
    for (int i = 0; i < INDEX_TIME_GBP.size(); ++i) {
      INDEX_MONTH_GBP[i] = (int) (INDEX_TIME_GBP.get(i) * 12d - 0.1 / 12d);
    }
  }
  private static final InterpolatedNodalCurve INDEX_CURVE_GBP = InterpolatedNodalCurve.of(INDEX_META_GBP,
      DoubleArray.copyOf(INDEX_MONTH_GBP), INDEX_VALUE_GBP, INTERPOLATOR);

  private static final LocalDateDoubleTimeSeries INDEX_SERIES_GBP;
  static {
    LocalDateDoubleTimeSeriesBuilder builder = LocalDateDoubleTimeSeries.builder();
    builder.put(LocalDate.of(2013, 12, 31), 253.4);
    builder.put(LocalDate.of(2014, 1, 31), 252.6);
    builder.put(LocalDate.of(2014, 2, 28), 254.2);
    INDEX_SERIES_GBP = builder.build();
  }
  private static final PriceIndexValues INDEX_VALUES_GBP = ForwardPriceIndexValues.of(GB_RPI,
      YearMonth.from(VALUATION_DATE_GBP), INDEX_SERIES_GBP, INDEX_CURVE_GBP);

  //
  //  private static final PriceIndex GBP_RPI = PriceIndices.GB_RPI;
  //
  //  private static final ZonedDateTime STD_REFERENCE_DATE = DateUtils.getUTCDate(2014, 4, 11);
  //  private static final Pair<InflationProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_PAIR_STD = StandardDataSetsInflationGBP
  //      .getCurvesGBPRpiAndSonia();
  //  private static final InflationProviderDiscount INFLATION_MULTICURVE_STD = MULTICURVE_PAIR_STD.getFirst();
  //  
  //  private static final LocalDateDoubleTimeSeries TS_PRICE_INDEX_USD_WITH_TODAY = LocalDateDoubleTimeSeries.builder()
  //      .put(LocalDate.of(2013, 12, 31), 253.4)
  //      .put(LocalDate.of(2013, 12, 31), 253.4)
  //      .put(LocalDate.of(2013, 12, 31), 253.4)
  //      .build();
  //
  //  // price index curve
  //  private static final InterpolatedNodalCurve CURVE_GB;
  //  static {
  //    IndexPrice[] indexList = StandardDataSetsInflationGBP.indexONArray();
  //    IndexPrice gbpRpi = indexList[0];
  //    PriceIndexCurveSimple indexCurve = (PriceIndexCurveSimple) INFLATION_MULTICURVE_STD
  //        .getPriceIndexCurves().get(gbpRpi);
  //    InterpolatedDoublesCurve curve = (InterpolatedDoublesCurve) indexCurve.getCurve();
  //    DefaultCurveMetadata metaData = DefaultCurveMetadata.builder()
  //        .curveName(CurveName.of(curve.getName()))
  //        .xValueType(ValueType.MONTHS)
  //        .yValueType(ValueType.PRICE_INDEX)
  //        .build();
  //    int nData = curve.getXDataAsPrimitive().length;
  //    double[] xValueInt = new double[nData];
  //    for (int i = 0; i < nData; ++i) {
  //      //      System.out.println(curve.getXDataAsPrimitive()[i] + "\t" + (curve.getXDataAsPrimitive()[i] * 12d));
  //      xValueInt[i] = (int) (curve.getXDataAsPrimitive()[i] * 12d);
  //    }
  //    CURVE_GB = InterpolatedNodalCurve.builder()
  //        .extrapolatorLeft(new FlatExtrapolator1D())
  //        .extrapolatorRight(new FlatExtrapolator1D())
  //        .interpolator(new LinearInterpolator1D())
  //        .metadata(metaData)
  //        .xValues(xValueInt)
  //        .yValues(curve.getYDataAsPrimitive())
  //        .build();
  //  }
  // create rates provider
  private static final RatesProvider RATES_PROVIDER_GB = ImmutableRatesProvider.builder()
      .valuationDate(VALUATION_DATE_GBP)
      .fxMatrix(FxMatrix.empty())
      .discountCurves(ImmutableMap.of(GBP, DSC_CURVE_GBP))
      .priceIndexValues(ImmutableMap.of(GB_RPI, INDEX_VALUES_GBP))
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
          .index(GB_RPI)
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

  @Test
      (enabled = false)
  // TODO investigate this
  public void test() {
    System.out.println(pricer.presentValue(INFLATION_FIXED_SWAP_LEG_PAY_3, RATES_PROVIDER_GB)); // [GBP -1217486.2135038024]
    System.out.println(pricer.presentValue(INFLATION_MONTHLY_SWAP_LEG_REC_3, RATES_PROVIDER_GB)); // [GBP 1195567.2693405494]
    MultiCurrencyAmount pv = PRICER.presentValue(SWAP_INFLATION_3, RATES_PROVIDER_GB);
    System.out.println(pv); // -21922.072817862267  // [GBP -21918.94416324678] nearest
  }
}
