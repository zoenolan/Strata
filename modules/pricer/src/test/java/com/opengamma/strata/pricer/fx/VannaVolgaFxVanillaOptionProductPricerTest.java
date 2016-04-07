package com.opengamma.strata.pricer.fx;

import static com.opengamma.strata.basics.LongShort.LONG;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.interpolator.CurveExtrapolator;
import com.opengamma.strata.market.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.interpolator.CurveInterpolator;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.fx.ResolvedFxSingle;
import com.opengamma.strata.product.fx.ResolvedFxVanillaOption;

@Test
public class VannaVolgaFxVanillaOptionProductPricerTest {

  private static final ZoneId ZONE = ZoneId.of("Z");
  private static final ZonedDateTime VAL_DATETIME = ZonedDateTime.of(2011, 6, 13, 13, 10, 0, 0, ZONE);
  private static final LocalDate VAL_DATE = VAL_DATETIME.toLocalDate();
  private static final RatesProvider RATES_PROVIDER = RatesProviderFxDataSets.createProviderEurUsdActActIsda(VAL_DATE);

  private static final String NAME = "smileEurUsd";
  private static final DoubleArray TIME_TO_EXPIRY = DoubleArray.of(0.0001, 0.25205479452054796, 0.5013698630136987,
      1.0015120892282356, 2.0, 5.001512089228235);
  private static final DoubleArray ATM = DoubleArray.of(0.11, 0.115, 0.12, 0.12, 0.125, 0.13);
  private static final DoubleArray DELTA = DoubleArray.of(0.25);
  private static final DoubleMatrix RISK_REVERSAL = DoubleMatrix.ofUnsafe(new double[][] {
    {0.015 }, {0.020 }, {0.025 }, {0.03 }, {0.025 }, {0.030 } });
  private static final DoubleMatrix STRANGLE = DoubleMatrix.ofUnsafe(new double[][] {
    {0.002 }, {0.003 }, {0.004 }, {0.0045 }, {0.0045 }, {0.0045 } });
  private static final CurveInterpolator INTERP_STRIKE = CurveInterpolators.DOUBLE_QUADRATIC;
  private static final CurveExtrapolator EXTRAP_STRIKE = CurveExtrapolators.LINEAR;
  private static final InterpolatedSmileDeltaTermStructureStrikeInterpolation SMILE_TERM =
      InterpolatedSmileDeltaTermStructureStrikeInterpolation.of(NAME, TIME_TO_EXPIRY, DELTA, ATM, RISK_REVERSAL,
          STRANGLE, EXTRAP_STRIKE, INTERP_STRIKE, EXTRAP_STRIKE);
  private static final CurrencyPair CURRENCY_PAIR = CurrencyPair.of(EUR, USD);
  private static final BlackVolatilitySmileFxProvider VOL_PROVIDER =
      BlackVolatilitySmileFxProvider.of(SMILE_TERM, CURRENCY_PAIR, ACT_ACT_ISDA, VAL_DATETIME);

  private static final VannaVolgaFxVanillaOptionProductPricer PRICER = VannaVolgaFxVanillaOptionProductPricer.DEFAULT;

  public void test() {

    final int nbStrike = 10;
    final double strikeMin = 1.00;
    final double strikeRange = 0.80;
    final double[] strikes = new double[nbStrike + 1];
    final double[] pvExpected = new double[] {3.860405407112769E7, 3.0897699603079587E7, 2.3542824458812844E7,
      1.6993448607300103E7, 1.1705393621236656E7, 7865881.826,
      5312495.846, 3680367.677, 2607701.430, 1849818.30, 1282881.98 };
    final double notional = 100_000_000;
    final ZonedDateTime optionExpiry = ZonedDateTime.of(2012, 12, 13, 10, 0, 0, 0, ZONE);
    final LocalDate optionPay = LocalDate.of(2012, 12, 17);
    final ResolvedFxVanillaOption[] forexOption = new ResolvedFxVanillaOption[nbStrike + 1];
    for (int loopstrike = 0; loopstrike <= nbStrike; loopstrike++) {
      strikes[loopstrike] = strikeMin + loopstrike * strikeRange / nbStrike;
      CurrencyAmount eurAmount = CurrencyAmount.of(EUR, notional);
      CurrencyAmount usdAmount = CurrencyAmount.of(USD, -notional * strikes[loopstrike]);
      final ResolvedFxSingle forexUnderlyingDefinition = ResolvedFxSingle.of(eurAmount, usdAmount, optionPay);
      ResolvedFxVanillaOption option = ResolvedFxVanillaOption.builder()
          .longShort(LONG)
          .expiry(optionExpiry)
          .underlying(forexUnderlyingDefinition)
          .build();
      //      System.out.println(PRICER.presentValue(option, RATES_PROVIDER, VOL_PROVIDER) + "\t" + pvExpected[loopstrike]);
    }
  }
}
