package com.opengamma.strata.pricer.bond;


public class DiscountingCapitalIndexedBondTradePricer {

  //  public CurrencyAmount presentValueFromCleanRealPrice(final BondCapitalIndexedSecurity<?> bond, 
  //      final InflationIssuerProviderInterface market, final double cleanPriceReal) {
  //    Validate.notNull(bond, "Coupon");
  //    Validate.notNull(market, "Market");
  //    double settlementFactor = bond.getIndexRatio();
  //    final double cleanPriceNominal = cleanPriceReal * settlementFactor;
  //    return presentValueFromCleanNominalPrice(bond, market, cleanPriceNominal);
  //  }
  //
  //  /**
  //   * Computes the security present value from a quoted clean real price. 
  //   * The real accrued inflated and discounted from settlement are added to the discounted clean nominal price.
  //   * @param bond The bond security.
  //   * @param market The market.
  //   * @param cleanPriceNominal The nominal clean price.
  //   * @return The present value.
  //   */
  //  public CurrencyAmount presentValueFromCleanNominalPrice(final BondCapitalIndexedSecurity<?> bond, 
  //      final InflationIssuerProviderInterface market, final double cleanPriceNominal) {
  //    Validate.notNull(bond, "Coupon");
  //    Validate.notNull(market, "Market");
  //    Currency ccy = bond.getCurrency();
  //    final double notional = bond.getCoupon().getNthPayment(0).getNotional();
  //    final MultipleCurrencyAmount nominalAccruedInterest = 
  //        bond.getSettlement().accept(PVIC, market.getInflationProvider()).multipliedBy(bond.getAccruedInterest());
  //    double dfSettle = market.getDiscountFactor(ccy, bond.getSettlementTime());
  //    double pvPrice = dfSettle * cleanPriceNominal *  notional;
  //    return nominalAccruedInterest.plus(ccy, pvPrice);
  //  }
}
