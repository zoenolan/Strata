package com.opengamma.strata.pricer.impl.volatility.local;

import static com.opengamma.strata.basics.date.DayCounts.ACT_360;

import java.util.function.Function;

import org.testng.annotations.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.interpolator.CurveInterpolator;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.surface.ConstantNodalSurface;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.DeformedSurface;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.Surface;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolator;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;

@Test
public class DupireLocalVolatilityCalculatorTest {

  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.LINEAR.getName(), CurveExtrapolators.FLAT.getName(), CurveExtrapolators.FLAT.getName());
  private static final Interpolator1D TIMESQ_FLAT = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.TIME_SQUARE.getName(), CurveExtrapolators.FLAT.getName(), CurveExtrapolators.FLAT.getName());
  private static final Interpolator1D CUBIC = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.DOUBLE_QUADRATIC.getName(), CurveExtrapolators.LINEAR.getName(),
      CurveExtrapolators.LINEAR.getName());

  private static final GridInterpolator2D INTERPOLATOR_2D = new GridInterpolator2D(TIMESQ_FLAT, CUBIC);
  private static final DoubleArray TIMES = DoubleArray.of(0.25, 0.50, 1.00, 0.25, 0.50, 1.00, 0.25, 0.50, 1.00);
  private static final DoubleArray STRIKES = DoubleArray.of(0.8, 0.8, 0.8, 1.4, 1.4, 1.4, 2.0, 2.0, 2.0);
  private static final DoubleArray VOLS = DoubleArray.of(0.21, 0.19, 0.20, 0.12, 0.10, 0.10, 0.06, 0.06, 0.06);
  private static final InterpolatedNodalSurface SURFACE =
      InterpolatedNodalSurface.of(DefaultSurfaceMetadata.of("Test"), TIMES, STRIKES, VOLS, INTERPOLATOR_2D);

  private static final double SPOT = 1.40;
  private static final CurveInterpolator INTERPOLATOR = CurveInterpolators.LINEAR;
  private static final DoubleArray USD_DSC_TIME = DoubleArray.of(0.0, 0.5, 1.0, 2.0, 5.0);
  private static final DoubleArray USD_DSC_RATE = DoubleArray.of(0.0100, 0.0120, 0.0120, 0.0140, 0.0140);
  private static final CurveMetadata USD_DSC_METADATA = Curves.zeroRates("USD Dsc", ACT_360);
  private static final InterpolatedNodalCurve USD_DSC =
      InterpolatedNodalCurve.of(USD_DSC_METADATA, USD_DSC_TIME, USD_DSC_RATE, INTERPOLATOR);

  private static final DoubleArray EUR_DSC_TIME = DoubleArray.of(0.0, 0.5, 1.0, 2.0, 5.0);
  private static final DoubleArray EUR_DSC_RATE = DoubleArray.of(0.0150, 0.0125, 0.0150, 0.0175, 0.0150);
  private static final CurveMetadata EUR_DSC_METADATA = Curves.zeroRates("EUR Dsc", ACT_360);
  private static final InterpolatedNodalCurve EUR_DSC =
      InterpolatedNodalCurve.of(EUR_DSC_METADATA, EUR_DSC_TIME, EUR_DSC_RATE, INTERPOLATOR);

  public void flatVolTest() {
    double constantVol = 0.15;
    double tol = 1.0e-3;
    ConstantNodalSurface impliedVolSurface = ConstantNodalSurface.of("impliedVol", constantVol);
    Function<Double, Double> zeroRate = new Function<Double, Double>() {
      @Override
      public Double apply(Double x) {
        return 0d;
      }
    };
    DupireLocalVolatilityCalculator calc = new DupireLocalVolatilityCalculator();
    DeformedSurface localVolSurface =
        calc.getLocalVolatilityFromImpliedVolatility(impliedVolSurface, 100d, zeroRate, zeroRate);
    //    System.out.println(localVolSurface.zValue(1.0, 120d));
  }

  public void test() {
    DupireLocalVolatilityCalculator calc = new DupireLocalVolatilityCalculator();
    Function<Double, Double> interestRate = new Function<Double, Double>() {
      @Override
      public Double apply(Double x) {
        return 0.0d;
      }
    };
    Function<Double, Double> dividendRate = new Function<Double, Double>() {
      @Override
      public Double apply(Double x) {
        return 0.0d;
      }
    };
    Surface localVolSurface = calc.getLocalVolatilityFromImpliedVolatility(SURFACE, SPOT,
        interestRate, dividendRate);
    //    print(localVolSurface, SPOT);
  }

  private void print(Surface localVolSurface, double spot) {
    int nStrikes = 50;
    int nTimes = 50;
    double[] strikes = new double[nStrikes];
    for (int i = 0; i < nStrikes; ++i) {
      strikes[i] = spot * (0.5 + 0.02 * i);
      System.out.print("\t" + strikes[i]);
    }
    System.out.print("\n");
    for (int j = 0; j < nTimes; ++j) {
      double time = 0.02 + 0.02 * j;
      System.out.print(time);
      for (int i = 0; i < nStrikes; ++i) {
        System.out.print("\t" + localVolSurface.zValue(time, strikes[i]));
      }
      System.out.print("\n");
    }
  }
}
