package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
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
    double df = ratesProvider.discountFactor(ccyCounter, underlyingFx.getPaymentDate());
    ResolvedFxSingle underlying = option.getUnderlying();
    FxRate forward = fxPricer.forwardFxRate(underlying, ratesProvider);
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    double forwardRate = forward.fxRate(currencyPair);
    double strikeRate = option.getStrike();
    double timeToExpiry = volatilityProvider.relativeTime(option.getExpiry());
    boolean isCall = option.getPutCall().isCall();
    SmileDeltaParameters smileAtTime = volatilityProvider.getSmile().smileForTime(timeToExpiry);
    double[] strikes = smileAtTime.getStrike(forwardRate).toArray(); // TODO size should be 3
    double[] vols = smileAtTime.getVolatility().toArray(); // TODO size should be 3
    double volAtm = vols[1];
    double[] x = vannaVolgaWeights(forwardRate, strikeRate, timeToExpiry, volAtm, strikes);
    double priceFwd = BlackFormulaRepository.price(forwardRate, strikeRate, timeToExpiry, volAtm, isCall);
    for (int i = 0; i < 3; i = i + 2) {
      double priceFwdAtm = BlackFormulaRepository.price(forwardRate, strikes[i], timeToExpiry, volAtm, isCall);
      double priceFwdSmile = BlackFormulaRepository.price(forwardRate, strikes[i], timeToExpiry, vols[i], isCall);
      priceFwd += x[i] * (priceFwdSmile - priceFwdAtm);
    }
    return df * priceFwd;
  }

  public CurrencyAmount presentValue(
      ResolvedFxVanillaOption option,
      RatesProvider ratesProvider,
      BlackVolatilitySmileFxProvider volatilityProvider) {

    double price = price(option, ratesProvider, volatilityProvider);
    return CurrencyAmount.of(option.getCounterCurrency(), signedNotional(option) * price);
  }

  //-------------------------------------------------------------------------
  public PointSensitivityBuilder presentValueSensitivity(
      ResolvedFxVanillaOption option,
      RatesProvider ratesProvider,
      BlackVolatilitySmileFxProvider volatilityProvider) {

    ResolvedFxSingle underlyingFx = option.getUnderlying();
    Currency ccyCounter = option.getCounterCurrency();
    double df = ratesProvider.discountFactor(ccyCounter, underlyingFx.getPaymentDate());
    ResolvedFxSingle underlying = option.getUnderlying();
    FxRate forward = fxPricer.forwardFxRate(underlying, ratesProvider);
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    double forwardRate = forward.fxRate(currencyPair);
    double strikeRate = option.getStrike();
    double timeToExpiry = volatilityProvider.relativeTime(option.getExpiry());
    boolean isCall = option.getPutCall().isCall();
    SmileDeltaParameters smileAtTime = volatilityProvider.getSmile().smileForTime(timeToExpiry);
    double[] strikes = smileAtTime.getStrike(forwardRate).toArray(); // TODO size should be 3
    double[] vols = smileAtTime.getVolatility().toArray(); // TODO size should be 3
    double volAtm = vols[1];
    double[] x = vannaVolgaWeights(forwardRate, strikeRate, timeToExpiry, volAtm, strikes);
    double priceFwd = BlackFormulaRepository.price(forwardRate, strikeRate, timeToExpiry, volAtm, isCall);
    double deltaFwd = BlackFormulaRepository.delta(forwardRate, strikeRate, timeToExpiry, volAtm, isCall);
    for (int i = 0; i < 3; i = i + 2) {
      double priceFwdAtm = BlackFormulaRepository.price(forwardRate, strikes[i], timeToExpiry, volAtm, isCall);
      double priceFwdSmile = BlackFormulaRepository.price(forwardRate, strikes[i], timeToExpiry, vols[i], isCall);
      priceFwd += x[i] * (priceFwdSmile - priceFwdAtm);
      double deltaFwdAtm = BlackFormulaRepository.delta(forwardRate, strikes[i], timeToExpiry, volAtm, isCall);
      double deltaFwdSmile = BlackFormulaRepository.delta(forwardRate, strikes[i], timeToExpiry, vols[i], isCall);
      deltaFwd += x[i] * (deltaFwdSmile - deltaFwdAtm);
    }
    double signedNotional = signedNotional(option);
    PointSensitivityBuilder dfSensi = ratesProvider.discountFactors(ccyCounter)
        .zeroRatePointSensitivity(underlyingFx.getPaymentDate()).multipliedBy(priceFwd * signedNotional);
    PointSensitivityBuilder fwdSensi = fxPricer.forwardFxRatePointSensitivity(
        option.getPutCall().isCall() ? underlying : underlying.inverse(), ratesProvider)
        .multipliedBy(df * deltaFwd * signedNotional);
    return dfSensi.combinedWith(fwdSensi);
  }

  //-------------------------------------------------------------------------
  // signed notional amount to computed present value and value Greeks
  private double signedNotional(ResolvedFxVanillaOption option) {
    return (option.getLongShort().isLong() ? 1d : -1d) *
        Math.abs(option.getUnderlying().getBaseCurrencyPayment().getAmount());
  }

  private double[] vannaVolgaWeights(
      double forward,
      double strike,
      double timeToExpiry,
      double volATM,
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
