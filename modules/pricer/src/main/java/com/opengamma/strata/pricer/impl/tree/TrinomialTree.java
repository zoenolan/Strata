/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.tree;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;

/**
 * Trinomial tree.
 * <p>
 * Option pricing model based on trinomial tree. Trinomial lattice is defined by {@code LatticeSpecification} 
 * and the option to price is specified by {@code OptionFunction}. 
 */
public class TrinomialTree {

  /**
   * Price an option under the specified trinomial model.
   * <p>
   * It is assumed that the volatility, interest rate and continuous dividend rate are constant 
   * over the lifetime of the option. 
   * 
   * @param lattice  the lattice specification
   * @param function  the option
   * @param spot  the spot
   * @param volatility  the volatility
   * @param interestRate  the interest rate
   * @param dividendRate  the dividend rate
   * @return the option price
   */
  public double optionPrice(
      LatticeSpecification lattice,
      OptionFunction function,
      double spot,
      double volatility,
      double interestRate,
      double dividendRate) {

    int nSteps = function.getNumberOfSteps();
    double timeToExpiry = function.getTimeToExpiry();
    double dt = timeToExpiry / (double) nSteps;
    double discount = Math.exp(-interestRate * dt);
    DoubleArray params = lattice.getParametersTrinomial(volatility, interestRate - dividendRate, dt);
    double middleFactor = params.get(1);
    double downFactor = params.get(2);
    double upProbability = params.get(3);
    double midProbability = params.get(4);
    double downProbability = params.get(5);
    double middleOverDown = middleFactor / downFactor;
    ArgChecker.isTrue(upProbability > 0d, "upProbability should be greater than 0");
    ArgChecker.isTrue(upProbability < 1d, "upProbability should be smaller than 1");
    ArgChecker.isTrue(midProbability > 0d, "midProbability should be greater than 0");
    ArgChecker.isTrue(midProbability < 1d, "midProbability should be smaller than 1");
    ArgChecker.isTrue(downProbability > 0d, "downProbability should be greater than 0");
    DoubleArray values = function.getPayoffAtExpiryTrinomial(spot, downFactor, middleOverDown);
    for (int i = nSteps - 1; i > -1; --i) {
      values = function.getNextOptionValues(discount, upProbability, midProbability, downProbability, values, spot,
          downFactor, middleOverDown, i);
    }
    return values.get(0);
  }

}
