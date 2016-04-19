/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.volatility.local;

import java.util.function.Function;

import com.opengamma.strata.basics.PutCall;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.Surface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceName;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolator;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.impl.option.BlackScholesFormulaRepository;
import com.opengamma.strata.pricer.impl.tree.CoxRossRubinsteinLatticeSpecification;
import com.opengamma.strata.pricer.impl.tree.EuropeanVanillaOptionFunction;
import com.opengamma.strata.pricer.impl.tree.LatticeSpecification;
import com.opengamma.strata.pricer.impl.tree.OptionFunction;
import com.opengamma.strata.pricer.impl.tree.TrinomialTree;

/**
 * Local volatility calculation based on trinomila tree model. 
 * <p>
 * Emanuel Derman, Iraj Kani and Neil Chriss, "Implied Trinomial Trees of the Volatility Smile" (1996).
 */
public class ImpliedTrinomialTreeLocalVolatilityCalculator implements LocalVolatilityCalculator {

  /**
   * Trinomial tree. 
   */
  private static final TrinomialTree TREE = new TrinomialTree();
  /**
   * Default interpolator and extrapolator for strike dimension. 
   */
  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.LINEAR.getName(), CurveExtrapolators.FLAT.getName(), CurveExtrapolators.FLAT.getName());
  /**
   * Default interpolator and extrapolator for time dimension. 
   */
  private static final Interpolator1D TIMESQ_FLAT = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.TIME_SQUARE.getName(), CurveExtrapolators.FLAT.getName(), CurveExtrapolators.FLAT.getName());

  /**
   * The number of steps in trinomial tree. 
   */
  private final int nSteps;
  /**
   * The maximum value of time in trinomial tree. 
   * <p>
   * The time step in the tree is then given by {@code maxTime/nSteps}. 
   */
  private final double maxTime;
  /**
   * The interpolator for local volatilities. 
   * <p>
   * The resulting local volatilities are interpolated by this {@code GridInterpolator2D} along time and strike dimensions. 
   */
  private final GridInterpolator2D interpolator;

  /**
   * Creates an instance with default setups. 
   */
  public ImpliedTrinomialTreeLocalVolatilityCalculator() {
    this.nSteps = 20;
    this.maxTime = 3d;
    this.interpolator = new GridInterpolator2D(TIMESQ_FLAT, LINEAR_FLAT);
  }

  /**
   * Creates an instance by specifying the number of steps, maximum time, and 2D interpolator.
   * 
   * @param nSteps  number of steps 
   * @param maxTime  the maximum time 
   * @param interpolator  the interpolator
   */
  public ImpliedTrinomialTreeLocalVolatilityCalculator(int nSteps, double maxTime, GridInterpolator2D interpolator) {
    this.nSteps = nSteps;
    this.maxTime = maxTime;
    this.interpolator = interpolator;
  }

  @Override
  public InterpolatedNodalSurface localVolatilityFromImpliedVolatility(
      Surface impliedVolatilitySurface,
      double spot,
      Function<Double, Double> interestRate,
      Function<Double, Double> dividendRate) {

    int nTotal = (nSteps - 1) * (nSteps - 1) + 1;
    double[] timeRes = new double[nTotal];
    double[] spotRes = new double[nTotal];
    double[] volRes = new double[nTotal];
    // uniform grid based on CoxRossRubinsteinLatticeSpecification
    double volatility = impliedVolatilitySurface.zValue(maxTime, spot);
    double dt = maxTime / nSteps;
    double dx = volatility * Math.sqrt(2d * dt);
    double upFactor = Math.exp(dx);
    double downFactor = Math.exp(-dx);
    double[] adSec = new double[2 * nSteps + 1];
    double[] assetPrice = new double[2 * nSteps + 1];
    for (int i = nSteps; i > -1; --i) {
      if (i == 0) {
        double discountFactor = Math.exp(-interestRate.apply(dt) * dt);
        double fwdFactor = Math.exp((interestRate.apply(dt) - dividendRate.apply(dt)) * dt);
        double upProb = adSec[2] / discountFactor;
        double midProb = getMiddle(upProb, fwdFactor, spot, assetPrice[0], assetPrice[1], assetPrice[2]);
        double dwProb = 1d - upProb - midProb;
        double fwd = spot * fwdFactor;
        timeRes[nTotal - 1] = dt;
        spotRes[nTotal - 1] = spot;
        double var = (dwProb * Math.pow(assetPrice[0] - fwd, 2)
            + midProb * Math.pow(assetPrice[1] - fwd, 2)
            + upProb * Math.pow(assetPrice[2] - fwd, 2)) / (fwd * fwd * dt);
        volRes[nTotal - 1] = Math.sqrt(0.5 * (var + volRes[nTotal - 2] * volRes[nTotal - 2]));
      } else {
        double time = dt * i;
        double timeNext = dt * (i - 1);
        double zeroRate = interestRate.apply(time);
        double zeroDividendRate = dividendRate.apply(time);
        double zeroCostRate = zeroRate - zeroDividendRate;
        double rate = (zeroRate * time - interestRate.apply(timeNext) * timeNext) / dt;
        double dividend = (zeroDividendRate * time - dividendRate.apply(timeNext) * timeNext) / dt;
        double cost = rate - dividend;
        double discountFactor = Math.exp(-rate * dt);
        double fwdFactor = Math.exp(cost * dt);
        int nNodes = 2 * i + 1;
        double[] assetPriceLocal = new double[nNodes];
        double[] callOptionPrice = new double[nNodes];
        double[] putOptionPrice = new double[nNodes];
        int position = i - 1;
        double assetTmp = spot * Math.pow(upFactor, i);
        LatticeSpecification lattice = CoxRossRubinsteinLatticeSpecification.of(i);
        // call options for upper half nodes
        for (int j = nNodes - 1; j > position - 1; --j) {
          assetPriceLocal[j] = assetTmp;
          double impliedVol = impliedVolatilitySurface.zValue(time, assetPriceLocal[j]);
          OptionFunction call = EuropeanVanillaOptionFunction.of(assetPriceLocal[j], time, PutCall.CALL);
          double price = TREE.optionPrice(lattice, call, spot, impliedVol, zeroRate, zeroDividendRate);
          callOptionPrice[j] = price > 0d ? price : BlackScholesFormulaRepository.price(
              spot, assetPriceLocal[j], time, impliedVol, zeroRate, zeroCostRate, true);
          assetTmp *= downFactor;
        }
        // put options for lower half nodes
        assetTmp = spot * Math.pow(downFactor, i);
        for (int j = 0; j < position + 2; ++j) {
          assetPriceLocal[j] = assetTmp;
          double impliedVol = impliedVolatilitySurface.zValue(time, assetPriceLocal[j]);
          OptionFunction put = EuropeanVanillaOptionFunction.of(assetPriceLocal[j], time, PutCall.PUT);
          double price = TREE.optionPrice(lattice, put, spot, impliedVol, zeroRate, zeroDividendRate);
          putOptionPrice[j] = price >= 0d ? price : BlackScholesFormulaRepository.price(
              spot, assetPriceLocal[j], time, impliedVol, zeroRate, zeroCostRate, false);
          assetTmp *= upFactor;
        }
        double[] adSecLocal = new double[nNodes];
        // AD security prices from call options
        for (int j = nNodes - 1; j > position; --j) {
          adSecLocal[j] = callOptionPrice[j - 1];
          for (int k = j + 1; k < nNodes; ++k) {
            adSecLocal[j] -= (assetPriceLocal[k] - assetPriceLocal[j - 1]) * adSecLocal[k];
          }
          adSecLocal[j] /= (assetPriceLocal[j] - assetPriceLocal[j - 1]);
        }
        ++position;
        // AD security prices from put options
        for (int j = 0; j < position; ++j) {
          adSecLocal[j] = putOptionPrice[j + 1];
          for (int k = 0; k < j; ++k) {
            adSecLocal[j] -= (assetPriceLocal[j + 1] - assetPriceLocal[k]) * adSecLocal[k];
          }
          adSecLocal[j] /= (assetPriceLocal[j + 1] - assetPriceLocal[j]);
        }
        if (i != nSteps) {
          double[][] prob = new double[nNodes][3];
          // highest node
          prob[nNodes - 1][2] = adSec[nNodes + 1] / adSecLocal[nNodes - 1] / discountFactor;
          prob[nNodes - 1][1] = getMiddle(prob[nNodes - 1][2], fwdFactor, assetPriceLocal[nNodes - 1],
              assetPrice[nNodes - 1], assetPrice[nNodes], assetPrice[nNodes + 1]);
          prob[nNodes - 1][0] = 1d - prob[nNodes - 1][2] - prob[nNodes - 1][1];
          correctProbability(prob[nNodes - 1], fwdFactor, assetPriceLocal[nNodes - 1], assetPrice[nNodes - 1],
              assetPrice[nNodes], assetPrice[nNodes + 1]);
          // second highest node
          prob[nNodes - 2][2] = (adSec[nNodes] / discountFactor - prob[nNodes - 1][1] * adSecLocal[nNodes - 1]) /
              adSecLocal[nNodes - 2];
          prob[nNodes - 2][1] = getMiddle(prob[nNodes - 2][2], fwdFactor, assetPriceLocal[nNodes - 2],
              assetPrice[nNodes - 2], assetPrice[nNodes - 1], assetPrice[nNodes]);
          prob[nNodes - 2][0] = 1d - prob[nNodes - 2][2] - prob[nNodes - 2][1];
          correctProbability(prob[nNodes - 2], fwdFactor, assetPriceLocal[nNodes - 2], assetPrice[nNodes - 2],
              assetPrice[nNodes - 1], assetPrice[nNodes]);
          // subsequent nodes 
          for (int j = nNodes - 3; j > -1; --j) {
            prob[j][2] = (adSec[j + 2] / discountFactor - prob[j + 2][0] * adSecLocal[j + 2] - prob[j + 1][1] *
                adSecLocal[j + 1]) / adSecLocal[j];
            prob[j][1] = getMiddle(prob[j][2], fwdFactor, assetPriceLocal[j], assetPrice[j], assetPrice[j + 1],
                assetPrice[j + 2]);
            prob[j][0] = 1d - prob[j][1] - prob[j][2];
            correctProbability(prob[j], fwdFactor, assetPriceLocal[j], assetPrice[j], assetPrice[j + 1],
                assetPrice[j + 2]);
          }
          // local variance
          int offset = nTotal - i * i - 1;
          double[] varBare = new double[nNodes];
          for (int k = 0; k < nNodes; ++k) {
            double fwd = assetPriceLocal[k] * fwdFactor;
            varBare[k] = (prob[k][0] * Math.pow(assetPrice[k] - fwd, 2)
                + prob[k][1] * Math.pow(assetPrice[k + 1] - fwd, 2)
                + prob[k][2] * Math.pow(assetPrice[k + 2] - fwd, 2)) / (fwd * fwd * dt);
            if (varBare[k] < 0d) {
              throw new IllegalArgumentException("Negative variance");
            }
          }
          // smoothing
          for (int k = 0; k < nNodes - 2; ++k) {
            double var = k == 0 || k == nNodes - 3 ? (varBare[k] + varBare[k + 1] + varBare[k + 2]) / 3d :
                (varBare[k - 1] + varBare[k] + varBare[k + 1] + varBare[k + 2] + varBare[k + 3]) / 5d;
            volRes[offset + k] = i == nSteps - 1 ? Math.sqrt(var) :
                Math.sqrt(0.5 * (var + volRes[offset - (2 * i - k)] * volRes[offset - (2 * i - k)]));
            timeRes[offset + k] = dt * (i + 1d);
            spotRes[offset + k] = assetPriceLocal[k + 1];
          }
        }
        System.arraycopy(adSecLocal, 0, adSec, 0, nNodes);
        System.arraycopy(assetPriceLocal, 0, assetPrice, 0, nNodes);
      }
    }
    SurfaceMetadata metadata = DefaultSurfaceMetadata.builder()
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.STRIKE)
        .zValueType(ValueType.LOCAL_VOLATILITY)
        .surfaceName(SurfaceName.of("localVol_" + impliedVolatilitySurface.getName()))
        .build();
    return InterpolatedNodalSurface.of(
        metadata,
        DoubleArray.ofUnsafe(timeRes),
        DoubleArray.ofUnsafe(spotRes),
        DoubleArray.ofUnsafe(volRes),
        interpolator);
  }

  @Override
  public InterpolatedNodalSurface localVolatilityFromPrice(
      Surface callPriceSurface,
      double spot,
      Function<Double, Double> interestRate,
      Function<Double, Double> dividendRate) {

    int nTotal = (nSteps - 1) * (nSteps - 1) + 1;
    double[] timeRes = new double[nTotal];
    double[] spotRes = new double[nTotal];
    double[] volRes = new double[nTotal];
    // uniform grid based on CoxRossRubinsteinLatticeSpecification, using reference values
    double refPrice = callPriceSurface.zValue(maxTime, spot) * Math.exp(interestRate.apply(maxTime) * maxTime);
    double refForward = spot * Math.exp((interestRate.apply(maxTime) - dividendRate.apply(maxTime)) * maxTime);
    double refVolatility = BlackFormulaRepository.impliedVolatility(refPrice, refForward, spot, maxTime, true);
    double dt = maxTime / nSteps;
    double dx = refVolatility * Math.sqrt(2d * dt);
    double upFactor = Math.exp(dx);
    double downFactor = Math.exp(-dx);
    double[] adSec = new double[2 * nSteps + 1];
    double[] assetPrice = new double[2 * nSteps + 1];
    for (int i = nSteps; i > -1; --i) {
      if (i == 0) {
        double discountFactor = Math.exp(-interestRate.apply(dt) * dt);
        double fwdFactor = Math.exp((interestRate.apply(dt) - dividendRate.apply(dt)) * dt);
        double upProb = adSec[2] / discountFactor;
        double midProb = getMiddle(upProb, fwdFactor, spot, assetPrice[0], assetPrice[1], assetPrice[2]);
        double dwProb = 1d - upProb - midProb;
        double fwd = spot * fwdFactor;
        timeRes[nTotal - 1] = dt;
        spotRes[nTotal - 1] = spot;
        double var = (dwProb * Math.pow(assetPrice[0] - fwd, 2)
            + midProb * Math.pow(assetPrice[1] - fwd, 2)
            + upProb * Math.pow(assetPrice[2] - fwd, 2)) / (fwd * fwd * dt);
        volRes[nTotal - 1] = Math.sqrt(0.5 * (var + volRes[nTotal - 2] * volRes[nTotal - 2]));
      } else {
        double time = dt * i;
        double timeNext = dt * (i - 1);
        double zeroRate = interestRate.apply(time);
        double zeroDividendRate = dividendRate.apply(time);
        double rate = (zeroRate * time - interestRate.apply(timeNext) * timeNext) / dt;
        double dividend = (zeroDividendRate * time - dividendRate.apply(timeNext) * timeNext) / dt;
        double cost = rate - dividend;
        double discountFactor = Math.exp(-rate * dt);
        double fwdFactor = Math.exp(cost * dt);
        int nNodes = 2 * i + 1;
        double[] assetPriceLocal = new double[nNodes];
        double[] callOptionPrice = new double[nNodes];
        double[] putOptionPrice = new double[nNodes];
        int position = i - 1;
        double assetTmp = spot * Math.pow(upFactor, i);
        // call options for upper half nodes
        for (int j = nNodes - 1; j > position - 1; --j) {
          assetPriceLocal[j] = assetTmp;
          callOptionPrice[j] = callPriceSurface.zValue(time, assetPriceLocal[j]);
          assetTmp *= downFactor;
        }
        // put options for lower half nodes
        assetTmp = spot * Math.pow(downFactor, i);
        for (int j = 0; j < position + 2; ++j) {
          assetPriceLocal[j] = assetTmp;
          putOptionPrice[j] = callPriceSurface.zValue(time, assetPriceLocal[j])
              - spot * Math.exp(-zeroDividendRate * time) + Math.exp(-zeroRate * time) * assetPriceLocal[j];
          assetTmp *= upFactor;
        }
        double[] adSecLocal = new double[nNodes];
        // AD security prices from call options
        for (int j = nNodes - 1; j > position; --j) {
          adSecLocal[j] = callOptionPrice[j - 1];
          for (int k = j + 1; k < nNodes; ++k) {
            adSecLocal[j] -= (assetPriceLocal[k] - assetPriceLocal[j - 1]) * adSecLocal[k];
          }
          adSecLocal[j] /= (assetPriceLocal[j] - assetPriceLocal[j - 1]);
        }
        ++position;
        // AD security prices from put options
        for (int j = 0; j < position; ++j) {
          adSecLocal[j] = putOptionPrice[j + 1];
          for (int k = 0; k < j; ++k) {
            adSecLocal[j] -= (assetPriceLocal[j + 1] - assetPriceLocal[k]) * adSecLocal[k];
          }
          adSecLocal[j] /= (assetPriceLocal[j + 1] - assetPriceLocal[j]);
        }
        if (i != nSteps) {
          double[][] prob = new double[nNodes][3];
          // highest node
          prob[nNodes - 1][2] = adSec[nNodes + 1] / adSecLocal[nNodes - 1] / discountFactor;
          prob[nNodes - 1][1] = getMiddle(prob[nNodes - 1][2], fwdFactor, assetPriceLocal[nNodes - 1],
              assetPrice[nNodes - 1], assetPrice[nNodes], assetPrice[nNodes + 1]);
          prob[nNodes - 1][0] = 1d - prob[nNodes - 1][2] - prob[nNodes - 1][1];
          correctProbability(prob[nNodes - 1], fwdFactor, assetPriceLocal[nNodes - 1], assetPrice[nNodes - 1],
              assetPrice[nNodes], assetPrice[nNodes + 1]);
          // second highest node
          prob[nNodes - 2][2] = (adSec[nNodes] / discountFactor - prob[nNodes - 1][1] * adSecLocal[nNodes - 1]) /
              adSecLocal[nNodes - 2];
          prob[nNodes - 2][1] = getMiddle(prob[nNodes - 2][2], fwdFactor, assetPriceLocal[nNodes - 2],
              assetPrice[nNodes - 2], assetPrice[nNodes - 1], assetPrice[nNodes]);
          prob[nNodes - 2][0] = 1d - prob[nNodes - 2][2] - prob[nNodes - 2][1];
          correctProbability(prob[nNodes - 2], fwdFactor, assetPriceLocal[nNodes - 2], assetPrice[nNodes - 2],
              assetPrice[nNodes - 1], assetPrice[nNodes]);
          // subsequent nodes 
          for (int j = nNodes - 3; j > -1; --j) {
            prob[j][2] = (adSec[j + 2] / discountFactor - prob[j + 2][0] * adSecLocal[j + 2] - prob[j + 1][1] *
                adSecLocal[j + 1]) / adSecLocal[j];
            prob[j][1] = getMiddle(prob[j][2], fwdFactor, assetPriceLocal[j], assetPrice[j], assetPrice[j + 1],
                assetPrice[j + 2]);
            prob[j][0] = 1d - prob[j][1] - prob[j][2];
            correctProbability(prob[j], fwdFactor, assetPriceLocal[j], assetPrice[j], assetPrice[j + 1],
                assetPrice[j + 2]);
          }
          // local variance 
          int offset = nTotal - i * i - 1;
          double[] varBare = new double[nNodes];
          for (int k = 0; k < nNodes; ++k) {
            double fwd = assetPriceLocal[k] * fwdFactor;
            varBare[k] = (prob[k][0] * Math.pow(assetPrice[k] - fwd, 2)
                + prob[k][1] * Math.pow(assetPrice[k + 1] - fwd, 2)
                + prob[k][2] * Math.pow(assetPrice[k + 2] - fwd, 2)) / (fwd * fwd * dt);
            if (varBare[k] < 0d) {
              throw new IllegalArgumentException("Negative variance");
            }
          }
          // smoothing
          for (int k = 0; k < nNodes - 2; ++k) {
            double var = k == 0 || k == nNodes - 3 ? (varBare[k] + varBare[k + 1] + varBare[k + 2]) / 3d :
                (varBare[k - 1] + varBare[k] + varBare[k + 1] + varBare[k + 2] + varBare[k + 3]) / 5d;
            volRes[offset + k] = i == nSteps - 1 ? Math.sqrt(var) :
                Math.sqrt(0.5 * (var + volRes[offset - (2 * i - k)] * volRes[offset - (2 * i - k)]));
            timeRes[offset + k] = dt * (i + 1d);
            spotRes[offset + k] = assetPriceLocal[k + 1];
          }
        }
        System.arraycopy(adSecLocal, 0, adSec, 0, nNodes);
        System.arraycopy(assetPriceLocal, 0, assetPrice, 0, nNodes);
      }
    }
    SurfaceMetadata metadata = DefaultSurfaceMetadata.builder()
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.STRIKE)
        .zValueType(ValueType.LOCAL_VOLATILITY)
        .surfaceName(SurfaceName.of("localVol_" + callPriceSurface.getName()))
        .build();
    return InterpolatedNodalSurface.of(
        metadata,
        DoubleArray.ofUnsafe(timeRes),
        DoubleArray.ofUnsafe(spotRes),
        DoubleArray.ofUnsafe(volRes),
        interpolator);
  }

  //-----------------------------------------------------------------------
  private void correctProbability(double[] probability, double factor, double assetBase, double assertPriceLow,
      double assertPriceMid, double assetPriceHigh) {
    if (!(probability[2] > 0d && probability[1] > 0d && probability[0] > 0d)) {
      double fwd = assetBase * factor;
      if (fwd <= assertPriceMid && fwd > assertPriceLow) {
        probability[0] = 0.5 * (fwd - assertPriceLow) / (assetPriceHigh - assertPriceLow);
        probability[2] = 0.5 * ((assetPriceHigh - fwd) / (assetPriceHigh - assertPriceLow)
            + (assertPriceMid - fwd) / (assertPriceMid - assertPriceLow));
      } else if (fwd < assetPriceHigh && fwd > assertPriceMid) {
        probability[0] = 0.5 * ((fwd - assertPriceMid) / (assetPriceHigh - assertPriceLow)
            + (fwd - assertPriceLow) / (assetPriceHigh - assertPriceLow));
        probability[2] = 0.5 * (assetPriceHigh - fwd) / assetPriceHigh;
      }
      probability[1] = 1d - probability[0] - probability[2];
    }
  }

  private double getMiddle(double upProbability, double factor, double assetBase, double assetPrevDw,
      double assetPrevMd, double assetPrevUp) {
    return (factor * assetBase - assetPrevDw - upProbability * (assetPrevUp - assetPrevDw)) / (assetPrevMd - assetPrevDw);
  }
}
