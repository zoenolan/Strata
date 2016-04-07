package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionResult;
import com.opengamma.strata.math.impl.linearalgebra.SVDecompositionCommons;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.fx.ResolvedFxSingle;
import com.opengamma.strata.product.fx.ResolvedFxVanillaOption;

public class VannaVolgaFxVanillaOptionProductPricer {

  public static final VannaVolgaFxVanillaOptionProductPricer DEFAULT = new VannaVolgaFxVanillaOptionProductPricer(
      DiscountingFxSingleProductPricer.DEFAULT);

  /**
   * Underlying FX pricer.
   */
  private final DiscountingFxSingleProductPricer fxPricer;

  /**
   * Creates an instance.
   * 
   * @param fxPricer  the pricer for {@link ResolvedFxSingle}
   */
  public VannaVolgaFxVanillaOptionProductPricer(
      DiscountingFxSingleProductPricer fxPricer) {
    this.fxPricer = ArgChecker.notNull(fxPricer, "fxPricer");
  }

  public double price(
      ResolvedFxVanillaOption option,
      RatesProvider ratesProvider,
      BlackVolatilitySmileFxProvider volatilityProvider) {

    ResolvedFxSingle underlyingFx = option.getUnderlying();
    Currency ccyCounter = option.getCounterCurrency();
    double dfCounter = ratesProvider.discountFactor(ccyCounter, underlyingFx.getPaymentDate());//0.9804933982869053 // OK
    ResolvedFxSingle underlying = option.getUnderlying();
    FxRate forward = fxPricer.forwardFxRate(underlying, ratesProvider);
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    double forwardRate = forward.fxRate(currencyPair); // 1.3931221717772266 OK
    //    double forwardRate = 1.14154;
    double strikeRate = option.getStrike();
    double timeToExpiry = volatilityProvider.relativeTime(option.getExpiry()); // 1.5015120892282356
    boolean isCall = option.getPutCall().isCall();
    SmileDeltaParameters smileAtTime = volatilityProvider.getSmile().smileForTime(timeToExpiry);

    // [1.2798195118783868, 1.4091288965805677, 1.5891318872855842]
    // [0.11455707236797896, 0.12335752812299337, 0.14117242640024633]
    double[] strikesVV = smileAtTime.getStrike(forwardRate).toArray(); // TODO size should be 3
    double[] volVV = smileAtTime.getVolatility().toArray(); // TODO size should be 3
    double volATM = volVV[1];
    double[] priceVVATM = new double[3]; // [0.1465755439391062, 0.0, 0.023313680399083197]
    double[] priceVVsmile = new double[3]; // [0.1418341141572143, 0.0, 0.032408235108741155]

    for (int loopvv = 0; loopvv < 3; loopvv = loopvv + 2) {
      priceVVATM[loopvv] = dfCounter *
          BlackFormulaRepository.price(forwardRate, strikesVV[loopvv], timeToExpiry, volATM, isCall);
      priceVVsmile[loopvv] = dfCounter *
          BlackFormulaRepository.price(forwardRate, strikesVV[loopvv], timeToExpiry, volVV[loopvv], isCall);
    }
    double[] x = vannaVolgaWeights(forwardRate, strikeRate, timeToExpiry, volATM, dfCounter, strikesVV);
    double price = BlackFormulaRepository.price(forwardRate, strikeRate, timeToExpiry, volATM, isCall) * dfCounter;
    //    System.out.println("black: " + price);
    for (int loopvv = 0; loopvv < 3; loopvv = loopvv + 2) {
      price += x[loopvv] * (priceVVsmile[loopvv] - priceVVATM[loopvv]);
    }
    return price;
  }

  public CurrencyAmount presentValue(
      ResolvedFxVanillaOption option,
      RatesProvider ratesProvider,
      BlackVolatilitySmileFxProvider volatilityProvider) {

    double price = price(option, ratesProvider, volatilityProvider);
    return CurrencyAmount.of(option.getCounterCurrency(), signedNotional(option) * price);
  }

  //-------------------------------------------------------------------------
  // signed notional amount to computed present value and value Greeks
  private double signedNotional(ResolvedFxVanillaOption option) {
    return (option.getLongShort().isLong() ? 1d : -1d) *
        Math.abs(option.getUnderlying().getBaseCurrencyPayment().getAmount());
  }

  /**
   * Matrix decomposition. 
   */
  private static final SVDecompositionCommons SVD = new SVDecompositionCommons();

  public double[] vannaVolgaWeights(
      double forward,
      double strike,
      double timeToExpiry,
      boolean isCall,
      double dfDomestic,
      double[] strikesReference,
      double[] volatilitiesReference) {

    double[][] matrix = new double[3][3];
    double[] vec = new double[3];
    double volATM = volatilitiesReference[1];
    for (int loopvv = 0; loopvv < 3; loopvv = loopvv + 2) {
      matrix[loopvv][0] = dfDomestic *
          BlackFormulaRepository.vega(forward, strikesReference[loopvv], timeToExpiry, volatilitiesReference[loopvv]);
      matrix[loopvv][1] = dfDomestic *
          BlackFormulaRepository.vanna(forward, strikesReference[loopvv], timeToExpiry, volatilitiesReference[loopvv]);
      matrix[loopvv][2] = dfDomestic *
          BlackFormulaRepository.volga(forward, strikesReference[loopvv], timeToExpiry, volatilitiesReference[loopvv]);
    }
    vec[0] = dfDomestic * BlackFormulaRepository.vega(forward, strike, timeToExpiry, volATM);
    vec[1] = dfDomestic * BlackFormulaRepository.vanna(forward, strike, timeToExpiry, volATM);
    vec[2] = dfDomestic * BlackFormulaRepository.volga(forward, strike, timeToExpiry, volATM);
    DecompositionResult decmp = SVD.apply(DoubleMatrix.ofUnsafe(matrix));
    return decmp.solve(vec);

  }

  public double[] vannaVolgaWeights(
      double forward,
      double strike,
      double timeToExpiry,
      double volATM,
      double dfDomestic,
      double[] strikesReference
      ) {

    double vega0 = BlackFormulaRepository.vega(forward, strikesReference[0], timeToExpiry, volATM);
    double vegaFlat = BlackFormulaRepository.vega(forward, strike, timeToExpiry, volATM);
    double vega2 = BlackFormulaRepository.vega(forward, strikesReference[2], timeToExpiry, volATM);
    double lnk21 = Math.log(strikesReference[1] / strikesReference[0]);
    double lnk31 = Math.log(strikesReference[2] / strikesReference[0]);
    double lnk32 = Math.log(strikesReference[2] / strikesReference[1]);
    double[] lnk = new double[3];
    for (int loopvv = 0; loopvv < 3; loopvv++) {
      lnk[loopvv] = Math.log(strikesReference[loopvv] / strike);
    }
    double[] x = new double[3];
    x[0] = vegaFlat * lnk[1] * lnk[2] / (vega0 * lnk21 * lnk31);
    x[2] = vegaFlat * lnk[0] * lnk[1] / (vega2 * lnk31 * lnk32);
    return x;
  }
}
