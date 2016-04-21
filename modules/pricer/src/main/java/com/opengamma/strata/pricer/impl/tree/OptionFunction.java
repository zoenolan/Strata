/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.tree;

import com.opengamma.strata.collect.array.DoubleArray;

/**
 * Option function interface used in tree option pricing.
 */
public interface OptionFunction {

  /**
   * Obtains strike value.
   * 
   * @return strike
   */
  public abstract double getStrike();

  /**
   * Obtains time to expiry.
   * 
   * @return time to expiry
   */
  public abstract double getTimeToExpiry();

  /**
   * Obtains number of time steps.
   * 
   * @return number of time steps
   */
  public abstract int getNumberOfSteps();

  /**
   * Computes payoff at expiry for trinomial tree.
   * <p>
   * The payoff values for individual nodes at expiry are computed.
   * If trinomial tree has {@code n} steps, the returned {@code DoubleArray} has the size {@code 2 * n + 1}.
   * 
   * @param spot  the spot
   * @param downFactor  the down factor
   * @param middleOverDown  middle factor divided by down factor
   * @return the payoff at 
   */
  public abstract DoubleArray getPayoffAtExpiryTrinomial(
      double spot,
      double downFactor,
      double middleOverDown);

  /**
   * Computes the option values in the intermediate nodes.
   * <p>
   * Given a set of option values in the (i+1)-th layer, option values in the i-th layer are derived.
   * For an option with path-dependence, this method should be overridden. 
   * <p>
   * The size of {@code values} must be (2*i+3). However, this is not checked because of its repeated usage.
   * 
   * @param discountFactor  the discount factor between the two layers
   * @param upProbability  the up probability
   * @param middleProbability  the middle probability
   * @param downProbability  the down probability
   * @param values  the option values in the (i+1)-th layer
   * @param spot  the spot
   * @param downFactor  the down factor
   * @param middleOverDown  middle factor divided by down factor
   * @param i  the steps
   * @return the option values in the i-th layer
   */
  public default DoubleArray getNextOptionValues(
      double discountFactor,
      double upProbability,
      double middleProbability,
      double downProbability,
      DoubleArray values,
      double spot,
      double downFactor,
      double middleOverDown,
      int i) {

    int nNodes = 2 * i + 1;
    double[] res = new double[nNodes];
    for (int j = 0; j < nNodes; ++j) {
      res[j] = discountFactor *
          (upProbability * values.get(j + 2) + middleProbability * values.get(j + 1) + downProbability * values.get(j));
    }
    return DoubleArray.ofUnsafe(res);
  }

}
