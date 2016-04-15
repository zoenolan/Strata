package com.opengamma.strata.pricer.impl.tree;


public interface OptionFunction {

  /**
   * Access strike price
   * @return _strike
   */
  public abstract double getStrike();

  /**
   * Access time to expiry
   * @return _timeToExpiry
   */
  public abstract double getTimeToExpiry();

  /**
   * Access signature in payoff formula
   * @return +1 if call, -1 if put
   */
  public abstract double getSign();

  public abstract double[] getPayoffAtExpiryTrinomial(double assetPrice, double downFactor, double middleOverDown,
      int nSteps);

  public default double[] getNextOptionValues(
      double discount,
      double upProbability,
      double middleProbability,
      double downProbability,
      double[] values,
      double baseAssetPrice,
      double downFactor,
      double middleOverDown,
      int steps) {

    int nNodes = 2 * steps + 1;
    double[] res = new double[nNodes];
    for (int j = 0; j < nNodes; ++j) {
      res[j] = discount *
          (upProbability * values[j + 2] + middleProbability * values[j + 1] + downProbability * values[j]);
    }
    return res;
  }

}
