package com.opengamma.strata.pricer.fx;

import static com.opengamma.strata.basics.LongShort.LONG;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
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

  private static final double NOTIONAL = 100_000_000d;
  private static final SimpleConstantContinuousBarrier BARRIER_KI = SimpleConstantContinuousBarrier.of(
      BarrierType.DOWN, KnockType.KNOCK_IN, 1.35);
  private static final SimpleConstantContinuousBarrier BARRIER_KO = SimpleConstantContinuousBarrier.of(
      BarrierType.DOWN, KnockType.KNOCK_OUT, 1.35);
  private static final CurrencyAmount REBATE = CurrencyAmount.of(USD, 50_000d);

  private static final LocalDate PAY_DATE = LocalDate.of(2014, 9, 15);
  private static final ZonedDateTime EXPIRY_DATETIME = PAY_DATE.atStartOfDay(ZONE);

  private static final CurrencyPair CURRENCY_PAIR = CurrencyPair.of(EUR, USD);
  private static final double STRIKE_RATE = 1.45;
  private static final CurrencyAmount EUR_AMOUNT = CurrencyAmount.of(EUR, NOTIONAL);
  private static final CurrencyAmount USD_AMOUNT = CurrencyAmount.of(USD, -NOTIONAL * STRIKE_RATE);

  private static final ResolvedFxSingle FX_PRODUCT = ResolvedFxSingle.of(EUR_AMOUNT, USD_AMOUNT, PAY_DATE);
  private static final ResolvedFxVanillaOption CALL = ResolvedFxVanillaOption.builder()
      .longShort(LONG)
      .expiry(EXPIRY_DATETIME)
      .underlying(FX_PRODUCT)
      .build();
  private static final ResolvedFxSingleBarrierOption CALL_KI = ResolvedFxSingleBarrierOption.of(CALL, BARRIER_KI,
      REBATE);

  private static final BlackFxSingleBarrierOptionProductPricer PRICER = BlackFxSingleBarrierOptionProductPricer.DEFAULT;
  private static final double TOL = 1.0e-13;

  public void regression_pv() {
    CurrencyAmount pv = PRICER.presentValue(CALL_KI, RATE_PROVIDER, VOL_PROVIDER);
    assertEquals(pv.getAmount(), 9035006.129433425, NOTIONAL * TOL);
  }

}
