package com.opengamma.strata.pricer.impl.tree;

import com.opengamma.strata.collect.ArgChecker;

public class TrinomialTree {

  /**
   * Price an option under the specified trinomial model.
   * <p>
   * The volatility, interest rate and continuous dividend rate are constant over the lifetime of the option. 
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

    int nSteps = lattice.getNumberOfSteps();
    double timeToExpiry = function.getTimeToExpiry();
    double dt = timeToExpiry / (double) nSteps;
    double discount = Math.exp(-interestRate * dt);
    double[] params = lattice.getParametersTrinomial(volatility, interestRate - dividendRate, dt);
    double middleFactor = params[1];
    double downFactor = params[2];
    double upProbability = params[3];
    double middleProbability = params[4];
    double downProbability = params[5];
    double middleOverDown = middleFactor / downFactor;
    ArgChecker.isTrue(upProbability > 0d, "upProbability should be greater than 0.");
    ArgChecker.isTrue(upProbability < 1d, "upProbability should be smaller than 1.");
    ArgChecker.isTrue(middleProbability > 0d, "middleProbability should be greater than 0.");
    ArgChecker.isTrue(middleProbability < 1d, "middleProbability should be smaller than 1.");
    ArgChecker.isTrue(downProbability > 0d, "downProbability should be greater than 0.");
    double[] values = function.getPayoffAtExpiryTrinomial(spot, downFactor, middleOverDown, nSteps);
    for (int i = nSteps - 1; i > -1; --i) {
      values = function.getNextOptionValues(
          discount, upProbability, middleProbability, downProbability, values, spot, downFactor, middleOverDown, i);
    }
    return values[0];
  }

}
