package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.sensitivity.FxOptionSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.view.DiscountFactors;
import com.opengamma.strata.pricer.impl.option.BlackBarrierPriceFormulaRepository;
import com.opengamma.strata.pricer.impl.option.BlackOneTouchAssetPriceFormulaRepository;
import com.opengamma.strata.pricer.impl.option.BlackOneTouchCashPriceFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.fx.ResolvedFxSingle;
import com.opengamma.strata.product.fx.ResolvedFxSingleBarrierOption;
import com.opengamma.strata.product.fx.ResolvedFxVanillaOption;
import com.opengamma.strata.product.fx.SimpleConstantContinuousBarrier;

public class BlackFxSingleBarrierOptionProductPricer {

  /**
   * Default implementation.
   */
  public static final BlackFxSingleBarrierOptionProductPricer DEFAULT = new BlackFxSingleBarrierOptionProductPricer();

  /**
   * Pricer for barrier option without rebate.
   */
  private static final BlackBarrierPriceFormulaRepository BARRIER_PRICER = new BlackBarrierPriceFormulaRepository();
  /**
   * Pricer for rebate.
   */
  private static final BlackOneTouchAssetPriceFormulaRepository ASSET_REBATE_PRICER = new BlackOneTouchAssetPriceFormulaRepository();
  /**
   * Pricer for rebate.
   */
  private static final BlackOneTouchCashPriceFormulaRepository CASH_REBATE_PRICER = new BlackOneTouchCashPriceFormulaRepository();

  /**
   * Creates an instance.
   */
  public BlackFxSingleBarrierOptionProductPricer() {
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the FX barrier option product.
   * <p>
   * The present value of the product is the value on the valuation date.
   * The price is represented in counter currency. 
   * 
   * @param option  the option product
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the present value of the product
   */
  public CurrencyAmount presentValue(
      ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    double price = price(option, ratesProvider, volatilityProvider);
    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
    return CurrencyAmount.of(underlyingOption.getCounterCurrency(), signedNotional(underlyingOption) * price);
  }

  /**
   * Calculates the price of the FX barrier option product.
   * <p>
   * The price of the product is the value on the valuation date.
   * The price is represented in units of counter currency. 
   * 
   * @param option  the option product
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the price of the product
   */
  public double price(
      ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    validate(option, ratesProvider, volatilityProvider);
    SimpleConstantContinuousBarrier barrier = (SimpleConstantContinuousBarrier) option.getBarrier();
    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
    ResolvedFxSingle underlyingFx = underlyingOption.getUnderlying();
    Currency ccyBase = underlyingFx.getBaseCurrencyPayment().getCurrency();
    Currency ccyCounter = underlyingFx.getCounterCurrencyPayment().getCurrency();
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    DiscountFactors baseDiscountFactors = ratesProvider.discountFactors(ccyBase);
    DiscountFactors counterDiscountFactors = ratesProvider.discountFactors(ccyCounter);

    double rateBase = baseDiscountFactors.zeroRate(underlyingFx.getPaymentDate());
    double rateCounter = counterDiscountFactors.zeroRate(underlyingFx.getPaymentDate());
    double costOfCarry = rateCounter - rateBase;
    double dfBase = baseDiscountFactors.discountFactor(underlyingFx.getPaymentDate());
    double dfCounter = counterDiscountFactors.discountFactor(underlyingFx.getPaymentDate());
    double spot = ratesProvider.fxRate(currencyPair);
    double strike = underlyingOption.getStrike();
    double forward = spot * dfBase / dfCounter;
    double volatility = volatilityProvider.getVolatility(currencyPair, underlyingOption.getExpiry(), strike, forward);
    double timeToExpiry = volatilityProvider.relativeTime(underlyingOption.getExpiry());
    double price = BARRIER_PRICER.price(
        spot, strike, timeToExpiry, costOfCarry, rateCounter, volatility, underlyingOption.getPutCall().isCall(), barrier);
    if (option.getRebate().isPresent()) {
      CurrencyAmount rebate = option.getRebate().get();
      double priceRebate = rebate.getCurrency().equals(ccyCounter) ?
          CASH_REBATE_PRICER.price(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType()) :
          ASSET_REBATE_PRICER.price(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType());
      price += priceRebate * rebate.getAmount() / Math.abs(underlyingFx.getBaseCurrencyPayment().getAmount());
    }
    return price;
  }

  //-------------------------------------------------------------------------
  public PointSensitivityBuilder presentValueSensitivity(
      ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    ValueDerivatives priceDerivatives = priceDerivatives(option, ratesProvider, volatilityProvider);
    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
    ResolvedFxSingle underlyingFx = underlyingOption.getUnderlying();
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    double signedNotional = signedNotional(underlyingOption);
    ZeroRateSensitivity counterSensi = ZeroRateSensitivity.of(
        currencyPair.getCounter(),
        underlyingFx.getPaymentDate(),
        signedNotional * (priceDerivatives.getDerivative(2) + priceDerivatives.getDerivative(3)));
    ZeroRateSensitivity baseSensi = ZeroRateSensitivity.of(
        currencyPair.getBase(),
        underlyingFx.getPaymentDate(),
        currencyPair.getCounter(),
        -priceDerivatives.getDerivative(3) * signedNotional);
    return counterSensi.combinedWith(baseSensi);
  }

  public FxOptionSensitivity presentValueSensitivityVolatility(
      ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    ValueDerivatives priceDerivatives = priceDerivatives(option, ratesProvider, volatilityProvider);
    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
    ResolvedFxSingle underlyingFx = underlyingOption.getUnderlying();
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    Currency ccyBase = currencyPair.getBase();
    Currency ccyCounter = currencyPair.getCounter();
    double dfBase = ratesProvider.discountFactor(ccyBase, underlyingFx.getPaymentDate());
    double dfCounter = ratesProvider.discountFactor(ccyCounter, underlyingFx.getPaymentDate());
    double spot = ratesProvider.fxRate(currencyPair);
    double forward = spot * dfBase / dfCounter;
    return FxOptionSensitivity.of(
        currencyPair,
        underlyingOption.getExpiry(),
        underlyingOption.getStrike(),
        forward,
        ccyCounter,
        priceDerivatives.getDerivative(4) * signedNotional(underlyingOption));
  }

  //-------------------------------------------------------------------------
  //  The derivatives are [0] spot, [1] strike, [2] rate, [3] cost-of-carry, [4] volatility, [5] timeToExpiry, [6] spot twice
  // [0] spot, [1] rate, [2] cost-of-carry, [3] volatility, [4] timeToExpiry, [5] spot twice. 
  private ValueDerivatives priceDerivatives(
      ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    validate(option, ratesProvider, volatilityProvider);
    SimpleConstantContinuousBarrier barrier = (SimpleConstantContinuousBarrier) option.getBarrier();
    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
    ResolvedFxSingle underlyingFx = underlyingOption.getUnderlying();
    CurrencyPair currencyPair = underlyingFx.getCurrencyPair();
    Currency ccyBase = currencyPair.getBase();
    Currency ccyCounter = currencyPair.getCounter();
    DiscountFactors baseDiscountFactors = ratesProvider.discountFactors(ccyBase);
    DiscountFactors counterDiscountFactors = ratesProvider.discountFactors(ccyCounter);

    double rateBase = baseDiscountFactors.zeroRate(underlyingFx.getPaymentDate());
    double rateCounter = counterDiscountFactors.zeroRate(underlyingFx.getPaymentDate());
    double costOfCarry = rateCounter - rateBase;
    double dfBase = baseDiscountFactors.discountFactor(underlyingFx.getPaymentDate());
    double dfCounter = counterDiscountFactors.discountFactor(underlyingFx.getPaymentDate());
    double spot = ratesProvider.fxRate(currencyPair);
    double strike = underlyingOption.getStrike();
    double forward = spot * dfBase / dfCounter;
    double volatility = volatilityProvider.getVolatility(currencyPair, underlyingOption.getExpiry(), strike, forward);
    double timeToExpiry = volatilityProvider.relativeTime(underlyingOption.getExpiry());
    ValueDerivatives valueDerivatives = BARRIER_PRICER.priceAdjoint(
        spot, strike, timeToExpiry, costOfCarry, rateCounter, volatility, underlyingOption.getPutCall().isCall(), barrier);
    if (!option.getRebate().isPresent()) {
      return valueDerivatives;
    }
    CurrencyAmount rebate = option.getRebate().get();
    ValueDerivatives valueDerivativesRebate = rebate.getCurrency().equals(ccyCounter) ?
        CASH_REBATE_PRICER.priceAdjoint(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType()) :
        ASSET_REBATE_PRICER.priceAdjoint(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType());
    double rebateRate = rebate.getAmount() / Math.abs(underlyingFx.getBaseCurrencyPayment().getAmount());
    double price = valueDerivatives.getValue() + rebateRate * valueDerivativesRebate.getValue();
    double[] derivatives = new double[7];
    derivatives[0] = valueDerivatives.getDerivative(0) + rebateRate * valueDerivativesRebate.getDerivative(0);
    derivatives[1] = valueDerivatives.getDerivative(1);
    for (int i = 2; i < 7; ++i) {
      derivatives[i] = valueDerivatives.getDerivative(i) + rebateRate * valueDerivativesRebate.getDerivative(i - 1);
    }
    return ValueDerivatives.of(price, DoubleArray.ofUnsafe(derivatives));
  }

  //-------------------------------------------------------------------------
  private void validate(ResolvedFxSingleBarrierOption option,
      RatesProvider ratesProvider,
      BlackVolatilityFxProvider volatilityProvider) {

    ArgChecker.isTrue(option.getBarrier() instanceof SimpleConstantContinuousBarrier,
        "barrier should be SimpleConstantContinuousBarrier");
    ArgChecker.isTrue(ratesProvider.getValuationDate().isEqual(volatilityProvider.getValuationDateTime().toLocalDate()),
        "Volatility and rate data must be for the same date");
  }

  // signed notional amount to computed present value and value Greeks
  private double signedNotional(ResolvedFxVanillaOption option) {
    return (option.getLongShort().isLong() ? 1d : -1d) *
        Math.abs(option.getUnderlying().getBaseCurrencyPayment().getAmount());
  }
}
