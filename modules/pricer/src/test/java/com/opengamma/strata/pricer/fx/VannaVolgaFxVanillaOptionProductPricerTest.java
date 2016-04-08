package com.opengamma.strata.pricer.fx;

import static com.opengamma.strata.basics.LongShort.LONG;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static org.testng.Assert.assertEquals;

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

  private static final double TOL = 1.0e-13;
  private static final VannaVolgaFxVanillaOptionProductPricer PRICER = VannaVolgaFxVanillaOptionProductPricer.DEFAULT;

  public void test() {
    int nbStrike = 10;
    double strikeMin = 1.00;
    double strikeRange = 0.80;
    double[] strikes = new double[nbStrike + 1];
    double[] expected = new double[] {3.860405407112769E7, 3.0897699603079587E7, 2.3542824458812844E7,
      1.6993448607300103E7, 1.1705393621236656E7, 7865881.8260216825, 5312495.846331886, 3680367.6766224853,
      2607701.430445888, 1849818.297903138, 1282881.9812227674 };

    //    {[EUR Dsc, USD]= (0.0, 0.0, -1.016834993607875E8, -1.0687281893573801E8, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 7.321670893786977E7, 7.695325324735151E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -1.0021953885887374E8, -1.0533414661787288E8, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 7.743544635059586E7, 8.13872898657015E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -9.430418338541561E7, -9.911690666813123E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 7.69436064730077E7, 8.087034941308834E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -8.284596766339977E7, -8.707393192902757E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 7.031492781003796E7, 7.3903382511043E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -6.764082328040574E7, -7.109280762910862E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 5.900921722111582E7, 6.2020695857797466E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -5.2035331262043096E7, -5.4690904337366335E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 4.623499720852033E7, 4.859455581508104E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -3.862682913929568E7, -4.059811220715709E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 3.470937255296122E7, 3.64807319923551E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -2.8260648102423556E7, -2.970290309286549E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 2.554672963322189E7, 2.6850482405254934E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -2.0537629799871795E7, -2.1585747980437294E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 1.8614699892839946E7, 1.9564683195371673E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -1.4728101851302534E7, -1.5479736361515924E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 1.3364038126029937E7, 1.404605895619165E7, 0.0) }
    //    {[EUR Dsc, USD]= (0.0, 0.0, -1.0288414551608022E7, -1.0813473891259879E7, 0.0) , [USD Dsc, USD]= (0.0, 0.0, 9342412.029968219, 9819193.040939828, 0.0) }

    double notional = 100_000_000;
    ZonedDateTime optionExpiry = ZonedDateTime.of(2012, 12, 13, 10, 0, 0, 0, ZONE);
    LocalDate optionPay = LocalDate.of(2012, 12, 17);
    for (int i = 0; i <= nbStrike; ++i) {
      strikes[i] = strikeMin + i * strikeRange / nbStrike;
      CurrencyAmount eurAmount = CurrencyAmount.of(EUR, notional);
      CurrencyAmount usdAmount = CurrencyAmount.of(USD, -notional * strikes[i]);
      ResolvedFxSingle forexUnderlyingDefinition = ResolvedFxSingle.of(eurAmount, usdAmount, optionPay);
      ResolvedFxVanillaOption option = ResolvedFxVanillaOption.builder()
          .longShort(LONG)
          .expiry(optionExpiry)
          .underlying(forexUnderlyingDefinition)
          .build();
      CurrencyAmount computed = PRICER.presentValue(option, RATES_PROVIDER, VOL_PROVIDER);
      assertEquals(computed.getAmount(), expected[i], notional * TOL);
    }
  }
}
