package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.collect.ArgChecker;
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

  private static final BlackBarrierPriceFormulaRepository BARRIER_PRICER = new BlackBarrierPriceFormulaRepository();

  private static final BlackOneTouchAssetPriceFormulaRepository ASSET_REBATE_PRICER = new BlackOneTouchAssetPriceFormulaRepository();
  private static final BlackOneTouchCashPriceFormulaRepository CASH_REBATE_PRICER = new BlackOneTouchCashPriceFormulaRepository();

  /**
   * Creates an instance.
   */
  public BlackFxSingleBarrierOptionProductPricer() {
  }

  // TODO check val dates

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

    ArgChecker.isTrue(option.getBarrier() instanceof SimpleConstantContinuousBarrier,
        "barrier should be SimpleConstantContinuousBarrier");
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
    //    double volatility = 0.16005581964804308;
    double price = BARRIER_PRICER.price(spot, strike, timeToExpiry, costOfCarry, rateCounter, volatility,
        underlyingOption.getPutCall().isCall(), barrier);
    if (option.getRebate().isPresent()) {
      CurrencyAmount rebate = option.getRebate().get();
      double priceRebate = rebate.getCurrency().equals(underlyingOption.getCounterCurrency()) ?
          CASH_REBATE_PRICER.price(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType()) :
          ASSET_REBATE_PRICER.price(spot, timeToExpiry, costOfCarry, rateCounter, volatility, barrier.inverseKnockType());
      System.out
          .println(priceRebate * rebate.getAmount() / Math.abs(underlyingFx.getBaseCurrencyPayment().getAmount()));
      price += priceRebate * rebate.getAmount() / Math.abs(underlyingFx.getBaseCurrencyPayment().getAmount());
    }
    return price;
  }

  //-------------------------------------------------------------------------
  // signed notional amount to computed present value and value Greeks
  private double signedNotional(ResolvedFxVanillaOption option) {
    return (option.getLongShort().isLong() ? 1d : -1d) *
        Math.abs(option.getUnderlying().getBaseCurrencyPayment().getAmount());
  }
}
