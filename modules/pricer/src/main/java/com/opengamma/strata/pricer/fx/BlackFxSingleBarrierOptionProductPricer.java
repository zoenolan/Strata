package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.impl.option.BlackBarrierPriceFormulaRepository;
import com.opengamma.strata.pricer.impl.option.BlackOneTouchAssetPriceFormulaRepository;
import com.opengamma.strata.pricer.impl.option.BlackOneTouchCashPriceFormulaRepository;
import com.opengamma.strata.product.fx.ResolvedFxSingle;

public class BlackFxSingleBarrierOptionProductPricer {

  /**
   * Default implementation.
   */
  public static final BlackFxSingleBarrierOptionProductPricer DEFAULT =
      new BlackFxSingleBarrierOptionProductPricer(DiscountingFxSingleProductPricer.DEFAULT);

  private static final BlackBarrierPriceFormulaRepository BARRIER_PRICER = new BlackBarrierPriceFormulaRepository();
  private static final BlackOneTouchAssetPriceFormulaRepository ASSET_REBATE_PRICER = new BlackOneTouchAssetPriceFormulaRepository();
  private static final BlackOneTouchCashPriceFormulaRepository CASH_REBATE_PRICER = new BlackOneTouchCashPriceFormulaRepository();

  /**
   * Underlying FX pricer.
   */
  private final DiscountingFxSingleProductPricer fxPricer;

  /**
   * Creates an instance.
   * 
   * @param fxPricer  the pricer for {@link ResolvedFxSingle}
   */
  public BlackFxSingleBarrierOptionProductPricer(
      DiscountingFxSingleProductPricer fxPricer) {
    this.fxPricer = ArgChecker.notNull(fxPricer, "fxPricer");
  }

  //  public double price(
  //      ResolvedFxSingleBarrierOption option,
  //      RatesProvider ratesProvider,
  //      BlackVolatilityFxProvider volatilityProvider) {
  //
  //    ResolvedFxVanillaOption underlyingOption = option.getUnderlyingOption();
  //    ResolvedFxSingle underlyingFx = underlyingOption.getUnderlying();
  //    double timeToExpiry = volatilityProvider.relativeTime(underlyingOption.getExpiry());
  //    double timeToPayment = volatilityProvider.relativeTime(
  //        underlyingFx.getPaymentDate().atStartOfDay(underlyingOption.getExpiry().getZone())); // approximation
  //    double spot = ratesProvider.fxRate(underlyingFx.getCurrencyPair());
  //    FxRate forward = fxPricer.forwardFxRate(underlyingFx, ratesProvider);
  //    double rateCounter = ratesProvider.discountFactor(null, null)
  //    double forwardPrice = undiscountedPrice(option, ratesProvider, volatilityProvider);
  //    double discountFactor = ratesProvider.discountFactor(option.getCounterCurrency(), underlying.getPaymentDate());
  //    return discountFactor * forwardPrice;
  //  }

}
