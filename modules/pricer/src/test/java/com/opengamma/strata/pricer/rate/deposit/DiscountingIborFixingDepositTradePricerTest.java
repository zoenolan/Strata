/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.deposit;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.date.HolidayCalendars.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.finance.TradeInfo;
import com.opengamma.strata.finance.rate.deposit.IborFixingDeposit;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTrade;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.DiscountIborIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.SimpleRatesProvider;

/**
 * Tests {@link DiscountingIborFixingDepositTradePricer}.
 */
@Test
public class DiscountingIborFixingDepositTradePricerTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 1, 16);
  
  private static final LocalDate START_DATE = LocalDate.of(2014, 1, 24);
  private static final LocalDate END_DATE = LocalDate.of(2014, 7, 24);
  private static final double NOTIONAL = 100_000_000d;
  private static final double RATE = 0.0150;
  private static final BusinessDayAdjustment BD_ADJ = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA);
  private static final IborFixingDeposit DEPOSIT_PRODUCT = IborFixingDeposit.builder()
      .buySell(BuySell.BUY)
      .notional(NOTIONAL)
      .startDate(START_DATE)
      .endDate(END_DATE)
      .businessDayAdjustment(BD_ADJ)
      .index(EUR_EURIBOR_6M)
      .fixedRate(RATE)
      .build();
  private static final IborFixingDepositTrade DEPOSIT_TRADE = 
      IborFixingDepositTrade.builder().product(DEPOSIT_PRODUCT).tradeInfo(TradeInfo.EMPTY).build();  

  private static final DiscountFactors DISCOUNT_FACTORS;
  private static final IborIndexRates IBOR_RATES;
  static {
    CurveInterpolator interp = Interpolator1DFactory.DOUBLE_QUADRATIC_INSTANCE;
    double[] time_eur = new double[] {0.0, 0.1, 0.25, 0.5, 0.75, 1.0, 2.0};
    double[] rate_eur = new double[] {0.0160, 0.0165, 0.0155, 0.0155, 0.0155, 0.0150, 0.014};
    InterpolatedNodalCurve dscCurve =
        InterpolatedNodalCurve.of(Curves.zeroRates("EUR-Discount", ACT_ACT_ISDA), time_eur, rate_eur, interp);
    double[] time_index = new double[] {0.0, 0.25, 0.5, 1.0};
    double[] rate_index = new double[] {0.0180, 0.0180, 0.0175, 0.0165};
    InterpolatedNodalCurve indexCurve =
        InterpolatedNodalCurve.of(Curves.zeroRates("EUR-EURIBOR6M", ACT_ACT_ISDA), time_index, rate_index, interp);
    DISCOUNT_FACTORS = ZeroRateDiscountFactors.of(EUR, VALUATION_DATE, dscCurve);
    IBOR_RATES = DiscountIborIndexRates.of(EUR_EURIBOR_6M, ZeroRateDiscountFactors.of(EUR, VALUATION_DATE, indexCurve));
  }
  
  private static final DiscountingIborFixingDepositProductPricer PRICER_PRODUCT =
      DiscountingIborFixingDepositProductPricer.DEFAULT;
  private static final DiscountingIborFixingDepositTradePricer PRICER_TRADE =
      DiscountingIborFixingDepositTradePricer.DEFAULT;


  private static final double TOLERANCE_PV = 1E-2;
  private static final double TOLERANCE_PV_DELTA = 1E-2;
  private static final double TOLERANCE_RATE = 1E-8;
  
  //-------------------------------------------------------------------------
  public void present_value() {
    SimpleRatesProvider prov = new SimpleRatesProvider(VALUATION_DATE, DISCOUNT_FACTORS, IBOR_RATES);
    CurrencyAmount pvTrade = PRICER_TRADE.presentValue(DEPOSIT_TRADE, prov);
    CurrencyAmount pvProduct = PRICER_PRODUCT.presentValue(DEPOSIT_PRODUCT, prov);
    assertEquals(pvTrade.getCurrency(), pvProduct.getCurrency());
    assertEquals(pvTrade.getAmount(), pvProduct.getAmount(), TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void present_value_sensitivity() {
    SimpleRatesProvider prov = new SimpleRatesProvider(VALUATION_DATE, DISCOUNT_FACTORS, IBOR_RATES);
    PointSensitivities ptsTrade = PRICER_TRADE.presentValueSensitivity(DEPOSIT_TRADE, prov);
    PointSensitivities ptsProduct = PRICER_PRODUCT.presentValueSensitivity(DEPOSIT_PRODUCT, prov);
    assertTrue(ptsTrade.equalWithTolerance(ptsProduct, TOLERANCE_PV_DELTA));
  }


  //-------------------------------------------------------------------------
  public void par_spread() {
    SimpleRatesProvider prov = new SimpleRatesProvider(VALUATION_DATE, DISCOUNT_FACTORS, IBOR_RATES);
    double psTrade = PRICER_TRADE.parSpread(DEPOSIT_TRADE, prov);
    double psProduct = PRICER_PRODUCT.parSpread(DEPOSIT_PRODUCT, prov);
    assertEquals(psTrade, psProduct, TOLERANCE_RATE);
    
  }

  //-------------------------------------------------------------------------
  public void par_spread_sensitivity() {
    SimpleRatesProvider prov = new SimpleRatesProvider(VALUATION_DATE, DISCOUNT_FACTORS, IBOR_RATES);
    PointSensitivities ptsTrade = PRICER_TRADE.parSpreadSensitivity(DEPOSIT_TRADE, prov);
    PointSensitivities ptsProduct = PRICER_PRODUCT.parSpreadSensitivity(DEPOSIT_PRODUCT, prov);
    assertTrue(ptsTrade.equalWithTolerance(ptsProduct, TOLERANCE_PV_DELTA));    
  }

}
