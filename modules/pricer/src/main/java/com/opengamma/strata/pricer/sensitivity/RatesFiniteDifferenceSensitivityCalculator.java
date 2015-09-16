/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.sensitivity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.market.Perturbation;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.curve.perturb.IndexedCurvePointShift;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Computes the curve parameter sensitivity by finite difference.
 * <p>
 * This is based on an {@link ImmutableRatesProvider}, and calculates the sensitivity by finite difference.
 * The curves underlying the rates provider must be of type {@link NodalCurve}.
 */
public class RatesFiniteDifferenceSensitivityCalculator {

  /**
   * Default implementation. The shift is one basis point (0.0001).
   */
  public static final RatesFiniteDifferenceSensitivityCalculator DEFAULT =
      new RatesFiniteDifferenceSensitivityCalculator(1.0E-4);

  /**
   * The shift used for finite difference.
   */
  private final double shift;

  /**
   * Create an instance of the finite difference calculator.
   * 
   * @param shift  the shift used in the finite difference computation
   */
  public RatesFiniteDifferenceSensitivityCalculator(double shift) {
    this.shift = shift;
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the first order sensitivities of a function of a RatesProvider to a double by finite difference.
   * <p>
   * The curves underlying the rates provider must be convertible to a {@link NodalCurve}.
   * The finite difference is computed by forward type. 
   * The function should return a value in the same currency for any rate provider.
   * 
   * @param provider  the rates provider
   * @param valueFn  the function from a rate provider to a currency amount for which the sensitivity should be computed
   * @return the curve sensitivity
   */
  public CurveCurrencyParameterSensitivities sensitivity(
      ImmutableRatesProvider provider,
      Function<ImmutableRatesProvider, CurrencyAmount> valueFn) {

    CurrencyAmount valueInit = valueFn.apply(provider);
    CurveCurrencyParameterSensitivities discounting = discountSensitivity(provider, valueFn, valueInit);
    CurveCurrencyParameterSensitivities forward = indexSensitivity(provider, valueFn, valueInit);
    return discounting.combinedWith(forward);
  }

  // computes the sensitivity with respect to the curves
  private CurveCurrencyParameterSensitivities discountSensitivity(
      ImmutableRatesProvider provider,
      Function<ImmutableRatesProvider, CurrencyAmount> valueFn,
      CurrencyAmount baseAmount) {

    // use Perturbation to access Curve in order to mutate this list
    List<CurveCurrencyParameterSensitivity> result = new ArrayList<>();
    for (DiscountFactors baseDf : provider.getDiscountFactors().values()) {
      baseDf.applyPerturbation(new Perturbation<Curve>() {
        @Override
        public Curve applyTo(Curve baseCurve) {
          int size = baseCurve.getParameterCount();
          double[] sensitivity = new double[size];
          for (int i = 0; i < size; i++) {
            DiscountFactors bumped = baseDf.applyPerturbation(IndexedCurvePointShift.absolute(i, shift));
            ImmutableRatesProvider providerDscBumped = provider.toBuilder().discountFactors(bumped).build();
            CurrencyAmount bumpedAmount = valueFn.apply(providerDscBumped);
            sensitivity[i] = (bumpedAmount.getAmount() - baseAmount.getAmount()) / shift;
          }
          result.add(CurveCurrencyParameterSensitivity.of(baseCurve.getMetadata(), baseAmount.getCurrency(), sensitivity));
          return baseCurve;
        }
      });
    }
    return CurveCurrencyParameterSensitivities.of(result);
  }

  // computes the sensitivity with respect to the curves
  private CurveCurrencyParameterSensitivities indexSensitivity(
      ImmutableRatesProvider provider,
      Function<ImmutableRatesProvider, CurrencyAmount> valueFn,
      CurrencyAmount baseAmount) {

    // use Perturbation to access Curve in order to mutate this list
    List<CurveCurrencyParameterSensitivity> result = new ArrayList<>();
    for (IborIndexRates baseRates : provider.getIborIndexRates().values()) {
      baseRates.applyPerturbation(new Perturbation<Curve>() {
        @Override
        public Curve applyTo(Curve baseCurve) {
          int size = baseCurve.getParameterCount();
          double[] sensitivity = new double[size];
          for (int i = 0; i < size; i++) {
            IborIndexRates bumped = baseRates.applyPerturbation(IndexedCurvePointShift.absolute(i, shift));
            ImmutableRatesProvider providerDscBumped = provider.toBuilder().iborIndexRates(bumped).build();
            CurrencyAmount bumpedAmount = valueFn.apply(providerDscBumped);
            sensitivity[i] = (bumpedAmount.getAmount() - baseAmount.getAmount()) / shift;
          }
          result.add(CurveCurrencyParameterSensitivity.of(baseCurve.getMetadata(), baseAmount.getCurrency(), sensitivity));
          return baseCurve;
        }
      });
    }
    for (OvernightIndexRates baseRates : provider.getOvernightIndexRates().values()) {
      baseRates.applyPerturbation(new Perturbation<Curve>() {
        @Override
        public Curve applyTo(Curve baseCurve) {
          int size = baseCurve.getParameterCount();
          double[] sensitivity = new double[size];
          for (int i = 0; i < size; i++) {
            OvernightIndexRates bumped = baseRates.applyPerturbation(IndexedCurvePointShift.absolute(i, shift));
            ImmutableRatesProvider providerDscBumped = provider.toBuilder().overnightIndexRates(bumped).build();
            CurrencyAmount bumpedAmount = valueFn.apply(providerDscBumped);
            sensitivity[i] = (bumpedAmount.getAmount() - baseAmount.getAmount()) / shift;
          }
          result.add(CurveCurrencyParameterSensitivity.of(baseCurve.getMetadata(), baseAmount.getCurrency(), sensitivity));
          return baseCurve;
        }
      });
    }
    return CurveCurrencyParameterSensitivities.of(result);
  }

}
