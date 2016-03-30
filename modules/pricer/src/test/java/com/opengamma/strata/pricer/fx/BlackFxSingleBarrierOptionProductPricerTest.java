package com.opengamma.strata.pricer.fx;

import static com.opengamma.strata.basics.LongShort.LONG;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.google.common.math.DoubleMath;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.collect.DoubleArrayMath;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.FxOptionSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.surface.SurfaceCurrencyParameterSensitivity;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.fx.BarrierType;
import com.opengamma.strata.product.fx.KnockType;
import com.opengamma.strata.product.fx.ResolvedFxSingle;
import com.opengamma.strata.product.fx.ResolvedFxSingleBarrierOption;
import com.opengamma.strata.product.fx.ResolvedFxVanillaOption;
import com.opengamma.strata.product.fx.SimpleConstantContinuousBarrier;

@Test
public class BlackFxSingleBarrierOptionProductPricerTest {

  private static final ZoneId ZONE = ZoneId.of("Z");
  private static final LocalDate VAL_DATE = LocalDate.of(2011, 6, 13);
  private static final ZonedDateTime VAL_DATETIME = VAL_DATE.atStartOfDay(ZONE);

  private static final BlackVolatilitySmileFxProvider VOL_PROVIDER =
      FxVolatilitySmileDataSet.createVolatilitySmileProvider5(VAL_DATETIME);
  private static final RatesProvider RATE_PROVIDER =
      RatesProviderFxDataSets.createProviderEurUsdActActIsda(VAL_DATE);

  private static final CurrencyPair CURRENCY_PAIR = CurrencyPair.of(EUR, USD);
  private static final double SPOT = RATE_PROVIDER.fxRate(CURRENCY_PAIR);
  private static final double NOTIONAL = 100_000_000d;
  private static final double LEVEL = 1.35;
  private static final SimpleConstantContinuousBarrier BARRIER_KI = SimpleConstantContinuousBarrier.of(
      BarrierType.DOWN, KnockType.KNOCK_IN, LEVEL);
  private static final SimpleConstantContinuousBarrier BARRIER_KO = SimpleConstantContinuousBarrier.of(
      BarrierType.DOWN, KnockType.KNOCK_OUT, LEVEL);
  private static final CurrencyAmount REBATE = CurrencyAmount.of(USD, 50_000d);
  private static final CurrencyAmount REBATE_BASE = CurrencyAmount.of(EUR, 50_000d);

  private static final LocalDate PAY_DATE = LocalDate.of(2014, 9, 15);
  private static final ZonedDateTime EXPIRY_DATETIME = PAY_DATE.atStartOfDay(ZONE);

  private static final double STRIKE_RATE = 1.45;
  private static final CurrencyAmount EUR_AMOUNT = CurrencyAmount.of(EUR, NOTIONAL);
  private static final CurrencyAmount USD_AMOUNT = CurrencyAmount.of(USD, -NOTIONAL * STRIKE_RATE);

  private static final ResolvedFxSingle FX_PRODUCT = ResolvedFxSingle.of(EUR_AMOUNT, USD_AMOUNT, PAY_DATE);
  private static final ResolvedFxVanillaOption CALL = ResolvedFxVanillaOption.builder()
      .longShort(LONG)
      .expiry(EXPIRY_DATETIME)
      .underlying(FX_PRODUCT)
      .build();
  private static final ResolvedFxSingleBarrierOption CALL_KI =
      ResolvedFxSingleBarrierOption.of(CALL, BARRIER_KI, REBATE);
  private static final ResolvedFxSingleBarrierOption CALL_KI_BASE =
      ResolvedFxSingleBarrierOption.of(CALL, BARRIER_KI, REBATE_BASE);

  private static final BlackFxSingleBarrierOptionProductPricer PRICER = BlackFxSingleBarrierOptionProductPricer.DEFAULT;
  private static final double TOL = 1.0e-12;

  //-------------------------------------------------------------------------
  public void regression_pv() {
    CurrencyAmount pv = PRICER.presentValue(CALL_KI, RATE_PROVIDER, VOL_PROVIDER);
    assertEquals(pv.getAmount(), 9035006.129433425, NOTIONAL * TOL);
    CurrencyAmount pvInv = PRICER.presentValue(CALL_KI_BASE, RATE_PROVIDER, VOL_PROVIDER);
    assertEquals(pvInv.getAmount(), 9038656.396419544, NOTIONAL * TOL); // UI put on USD/EUR rate with FX conversion in 2.x
  }

  public void regression_curveSensitivity() {
    PointSensitivityBuilder point = PRICER.presentValueSensitivity(CALL_KI, RATE_PROVIDER, VOL_PROVIDER);
    CurveCurrencyParameterSensitivities pvSensi = RATE_PROVIDER.curveParameterSensitivity(point.build());
    double[] eurSensi = new double[] {0.0, 0.0, 0.0, -8.23599758653779E7, -5.943903918586236E7 };
    double[] usdSensi = new double[] {0.0, 0.0, 0.0, 6.526531701730868E7, 4.710185614928411E7 };
    assertTrue(DoubleArrayMath.fuzzyEquals(
        eurSensi,
        pvSensi.getSensitivity(RatesProviderFxDataSets.getCurveName(EUR), USD).getSensitivity().toArray(),
        NOTIONAL * TOL));
    assertTrue(DoubleArrayMath.fuzzyEquals(
        usdSensi,
        pvSensi.getSensitivity(RatesProviderFxDataSets.getCurveName(USD), USD).getSensitivity().toArray(),
        NOTIONAL * TOL));

    PointSensitivityBuilder pointBase = PRICER.presentValueSensitivity(CALL_KI_BASE, RATE_PROVIDER, VOL_PROVIDER);
    CurveCurrencyParameterSensitivities pvSensiBase =
        RATE_PROVIDER.curveParameterSensitivity(pointBase.build()).convertedTo(EUR, RATE_PROVIDER);
    double[] eurSensiBase = new double[] {0.0, 0.0, 0.0, -5.885393657463378E7, -4.247477498074986E7 };
    double[] usdSensiBase = new double[] {0.0, 0.0, 0.0, 4.663853277047497E7, 3.365894110322015E7 };
    assertTrue(DoubleArrayMath.fuzzyEquals(
        eurSensiBase,
        pvSensiBase.getSensitivity(RatesProviderFxDataSets.getCurveName(EUR), EUR).getSensitivity().toArray(),
        NOTIONAL * TOL));
    assertTrue(DoubleArrayMath.fuzzyEquals(
        usdSensiBase,
        pvSensiBase.getSensitivity(RatesProviderFxDataSets.getCurveName(USD), EUR).getSensitivity().toArray(),
        NOTIONAL * TOL));
  }

  public void regression_volSensitivity() {
    PointSensitivityBuilder point = PRICER.presentValueSensitivityVolatility(CALL_KI, RATE_PROVIDER, VOL_PROVIDER);
    SurfaceCurrencyParameterSensitivity pvSensi = VOL_PROVIDER.surfaceParameterSensitivity((FxOptionSensitivity) point);
    PointSensitivityBuilder pointBase =
        PRICER.presentValueSensitivityVolatility(CALL_KI_BASE, RATE_PROVIDER, VOL_PROVIDER);
    SurfaceCurrencyParameterSensitivity pvSensiBase = VOL_PROVIDER
        .surfaceParameterSensitivity((FxOptionSensitivity) pointBase).convertedTo(EUR, RATE_PROVIDER);
    double[] computed = pvSensi.getSensitivity().toArray();
    double[] computedBase = pvSensiBase.getSensitivity().toArray();
    double[][] expected = new double[][] { {0.0, 0.0, 0.0, 0.0, 0.0 }, {0.0, 0.0, 0.0, 0.0, 0.0 },
      {0.0, 0.0, 0.0, 0.0, 0.0 }, {0.0, 0.0, 3.154862889936005E7, 186467.57005640838, 0.0 },
      {0.0, 0.0, 5.688931113627187E7, 336243.18963600876, 0.0 } };
    double[][] expectedBase = new double[][] { {0.0, 0.0, 0.0, 0.0, 0.0 }, {0.0, 0.0, 0.0, 0.0, 0.0 },
      {0.0, 0.0, 0.0, 0.0, 0.0 }, {0.0, 0.0, 2.2532363577178854E7, 133177.10564432456, 0.0 },
      {0.0, 0.0, 4.063094615828866E7, 240148.4331822043, 0.0 } };
    for (int i = 0; i < computed.length; ++i) {
      assertTrue(DoubleMath.fuzzyEquals(computed[i], expected[i / 5][i % 5], NOTIONAL * TOL));
      assertTrue(DoubleMath.fuzzyEquals(computedBase[i], expectedBase[i / 5][i % 5], NOTIONAL * TOL));
    }
  }

}
