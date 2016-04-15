package com.opengamma.strata.pricer.impl.tree;

public interface LatticeSpecification {

  public abstract double[] getParametersTrinomial(double volatility, double interestRate, double dt);

  public abstract int getNumberOfSteps();

}
