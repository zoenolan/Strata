/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.swaption;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.market.ReferenceData;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.NodalSurface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceName;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.statistics.leastsquare.LeastSquareResultsWithTransform;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.impl.option.RawOptionData;
import com.opengamma.strata.pricer.impl.option.SabrInterestRateParameters;
import com.opengamma.strata.pricer.impl.volatility.smile.fitting.SabrModelFitter;
import com.opengamma.strata.pricer.impl.volatility.smile.function.SabrFormulaData;
import com.opengamma.strata.pricer.impl.volatility.smile.function.SabrHaganVolatilityFunctionProvider;
import com.opengamma.strata.pricer.impl.volatility.smile.function.VolatilityFunctionProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrParametersSwaptionVolatilities;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;

/**
 * Utilities to calibrate SABR parameters to swaptions.
 */
public class SabrSwaptionCalibrationUtils {

  /** The reference data for calendars. This could be part of hte constructor.*/
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
  /** The SABR implied volatility function. */
  private final VolatilityFunctionProvider<SabrFormulaData> sabrFunctionProvider;
  /** The swap pricer. Required for forward rarte computation. */
  private final DiscountingSwapProductPricer swapPricer;
  
  /** The default instance of the class. */
  public static final SabrSwaptionCalibrationUtils DEFAULT = 
      new SabrSwaptionCalibrationUtils(
          SabrHaganVolatilityFunctionProvider.DEFAULT, DiscountingSwapProductPricer.DEFAULT);
  
  /**
   * Constructor from a SABR volatility function provider and a swap pricer.
   * <p>
   * The swap pricer is used to compute the forward rate required for calibration.
   * 
   * @param sabrFunctionProvider  the SABR implied volatility formula provider
   * @param swapPricer  the swap pricer
   */
  public SabrSwaptionCalibrationUtils(
      VolatilityFunctionProvider<SabrFormulaData> sabrFunctionProvider,
      DiscountingSwapProductPricer swapPricer) {
    this.sabrFunctionProvider = sabrFunctionProvider;
    this.swapPricer = swapPricer;
  }

  /**
   * Calibrate SABR parameters to a set of raw swaption normal volatilities. 
   * <p>
   * The SABR parameters are calibrated with fixed beta and shift surfaces.
   * 
   * @param convention  the swaption underlying convention
   * @param calibrationDateTime  the data and time of the calibration
   * @param dayCount  the day-count used for expiry time computation
   * @param tenors  the tenors associated to the different raw option data
   * @param normalVolatilities  the list of raw option normal volatility data
   * @param ratesProvider  the rate provider used to compute the swap forward rates
   * @param betaSurface  the beta surface
   * @param shiftSurface  the shift surface
   * @param interpolator  the interpolator for the alpha, rho and nu surfaces
   * @return the SABR volatility object
   */
  public SabrParametersSwaptionVolatilities calibrateFromNormalVolatilitiesFixedBetaShift(
      FixedIborSwapConvention convention,
      ZonedDateTime calibrationDateTime,
      DayCount dayCount,
      List<Tenor> tenors,
      List<RawOptionData> normalVolatilities,
      RatesProvider ratesProvider,
      NodalSurface betaSurface,
      NodalSurface shiftSurface, 
      GridInterpolator2D interpolator) {
    BitSet fixed = new BitSet();
    fixed.set(1); // Beta fixed
    int nbTenors = tenors.size();
    LocalDate calibrationDate = calibrationDateTime.toLocalDate();
    DoubleArray timeToExpiryArray = DoubleArray.EMPTY;
    DoubleArray timeTenorArray = DoubleArray.EMPTY;
    DoubleArray alphaArray = DoubleArray.EMPTY;
    DoubleArray rhoArray = DoubleArray.EMPTY;
    DoubleArray nuArray = DoubleArray.EMPTY;
    for (int looptenor = 0; looptenor < nbTenors; looptenor++) {
      double timeTenor = tenors.get(looptenor).getPeriod().getYears() 
          + tenors.get(looptenor).getPeriod().getMonths() / 12;
      List<Period> expiries = normalVolatilities.get(looptenor).getExpiries();
      int nbExpiries = expiries.size();
      for (int loopexpiry = 0; loopexpiry < nbExpiries; loopexpiry++) {
        LocalDate exerciseDate = expirationDate(convention.getFloatingLeg().getStartDateBusinessDayAdjustment(),
            calibrationDate, expiries.get(loopexpiry));
        LocalDate effectiveDate = convention.calculateSpotDateFromTradeDate(exerciseDate, REF_DATA);
        double timeToExpiry = dayCount.relativeYearFraction(calibrationDate, exerciseDate);
        timeToExpiryArray = timeToExpiryArray.concat(new double[] {timeToExpiry });
        timeTenorArray = timeTenorArray.concat(new double[] {timeTenor });
        double beta = betaSurface.zValue(timeToExpiry, timeTenor);
        double shift = shiftSurface.zValue(timeToExpiry, timeTenor);
        LocalDate endDate = effectiveDate.plus(tenors.get(looptenor));
        SwapTrade swap0 = convention.toTrade(calibrationDate, effectiveDate, endDate, BuySell.BUY, 1.0, 0.0);
        double forward = swapPricer.parRate(swap0.getProduct().resolve(REF_DATA), ratesProvider);
        Pair<DoubleArray, DoubleArray> availableSmile = normalVolatilities.get(looptenor).availableSmileAtExpiry(expiries.get(loopexpiry));
        double alphaStart = availableSmile.getSecond().get(0) / Math.pow(forward + shift, beta); // To improve ?
        DoubleArray startParameters = DoubleArray.ofUnsafe(new double[] {alphaStart, beta, 0.0, 0.10 }); 
        // TODO: To improve start parameters
        SabrFormulaData sabrPoint = calibrateShiftedFromNormalVolatilities(
            convention.getFloatingLeg().getStartDateBusinessDayAdjustment(), calibrationDateTime, dayCount, 
            expiries.get(loopexpiry), forward, availableSmile.getFirst(), normalVolatilities.get(looptenor).getStrikeType(),
            availableSmile.getSecond(), startParameters, fixed, shift);
        alphaArray = alphaArray.concat(new double[] {sabrPoint.getAlpha()});
        rhoArray = rhoArray.concat(new double[] {sabrPoint.getRho()});
        nuArray = nuArray.concat(new double[] {sabrPoint.getNu()});
      }
    }
    SurfaceMetadata metadata = DefaultSurfaceMetadata.builder().dayCount(dayCount)
        .surfaceName(SurfaceName.of("SABR parameter"))
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(normalVolatilities.get(0).getStrikeType())
        .zValueType(ValueType.UNKNOWN).build();
    InterpolatedNodalSurface alphaSurface = InterpolatedNodalSurface
        .of(metadata, timeToExpiryArray, timeTenorArray, alphaArray, interpolator);
    InterpolatedNodalSurface rhoSurface = InterpolatedNodalSurface
        .of(metadata, timeToExpiryArray, timeTenorArray, rhoArray, interpolator);
    InterpolatedNodalSurface nuSurface = InterpolatedNodalSurface
        .of(metadata, timeToExpiryArray, timeTenorArray, nuArray, interpolator);
    return SabrParametersSwaptionVolatilities.of(
        SabrInterestRateParameters.of(alphaSurface, betaSurface, rhoSurface, nuSurface, sabrFunctionProvider, shiftSurface), 
        convention, calibrationDateTime, dayCount);
  }

  /**
   * Calibrate the SABR parameters to a set of Black volatilities at given moneyness. All the associated swaptions
   * have the same expiration date, given by a period from calibration time, and the same tenor.
   * 
   * @param bda  the business day adjustment for the exercise date adjustment
   * @param calibrationDateTime  the calibration date and time
   * @param dayCount  the day count for the computation of the time to exercise
   * @param periodToExpiry  the period to expiry
   * @param forward  the forward price/rate
   * @param strikesLike  the options strike-like dimension
   * @param strikeType  the strike type
   * @param blackVolatilitiesInput  the option (call/payer) implied volatilities in shifted Black model
   * @param shiftInput  the shift used to computed the input implied shifted Black volatilities
   * @param startParameters  the starting parameters for the calibration. If one or more of the parameters are fixed,
   * the starting value will be used as the fixed parameter.
   * @param fixedParameters  the flag for the fixed parameters that are not calibrated
   * @param shiftOutput  the shift to calibrate the shifted SABR
   * @return SABR parameters
   */
  public SabrFormulaData calibrateShiftedFromBlackVolatilities(
      BusinessDayAdjustment bda,
      ZonedDateTime calibrationDateTime,
      DayCount dayCount,
      Period periodToExpiry,
      double forward,
      DoubleArray strikesLike,
      ValueType strikeType,
      DoubleArray blackVolatilitiesInput,
      double shiftInput,
      DoubleArray startParameters, 
      BitSet fixedParameters,
      double shiftOutput) {
    int nbStrikes = strikesLike.size();
    ArgChecker.isTrue(nbStrikes == blackVolatilitiesInput.size(), "size of strikes must be the same as size of volatilities");
    LocalDate calibrationDate = calibrationDateTime.toLocalDate();
    LocalDate exerciseDate = expirationDate(bda, calibrationDate, periodToExpiry);
    double timeToExpiry = dayCount.relativeYearFraction(calibrationDate, exerciseDate);
    double[] errors = new double[nbStrikes];
    Arrays.fill(errors, 1e-4);
    DoubleArray strikes = strikesShifted(forward, 0.0, strikesLike, strikeType);
    DoubleArray blackVolatilitiesTransformed = blackVolatilitiesShiftedFromBlackVolatilitiesShifted(
        forward, shiftOutput, timeToExpiry, strikes, blackVolatilitiesInput, shiftInput);
    DoubleArray strikesShifted = strikesShifted(forward, shiftOutput, strikesLike, strikeType);
    SabrModelFitter fitter = new SabrModelFitter(forward + shiftOutput, strikesShifted, timeToExpiry,
        blackVolatilitiesTransformed, DoubleArray.ofUnsafe(errors), sabrFunctionProvider);
    LeastSquareResultsWithTransform r = fitter.solve(startParameters, fixedParameters);
    return SabrFormulaData.of(r.getModelParameters().toArrayUnsafe());
  }
  
  /**
   * Creates an array of shifted Black volatilities from shifted Black volatilities with a different shift.
   * 
   * @param forward  the forward rate
   * @param shiftOutput  the shift required in the output
   * @param timeToExpiry  the time to expiration
   * @param strikes  the option strikes
   * @param blackVolatilities  the shifted implied Black volatilities
   * @param shiftInput  the shift used in the input Black implied volatilities
   * @return the shifted black volatilities
   */
  public DoubleArray blackVolatilitiesShiftedFromBlackVolatilitiesShifted(
      double forward, 
      double shiftOutput, 
      double timeToExpiry, 
      DoubleArray strikes,
      DoubleArray blackVolatilities,
      double shiftInput) {
    if(shiftInput == shiftOutput) { // No change required if shifts are the same
      return blackVolatilities; // FIXME: improve comparison between shifts
    }
    int nbStrikes = strikes.size();
    double[] bv = new double[nbStrikes];
    for (int i = 0; i < nbStrikes; i++) {
      double price = BlackFormulaRepository.price(
          forward + shiftInput, strikes.get(i) + shiftInput, timeToExpiry, blackVolatilities.get(i), true);
      bv[i] = BlackFormulaRepository.impliedVolatility(
          price, forward + shiftOutput, strikes.get(i) + shiftOutput, timeToExpiry, true); // FIXME: improve translation
    }
    return DoubleArray.ofUnsafe(bv);
  }

  /**
   * Calibrate the SABR parameters to a set of option prices at given moneyness. All the associated swaptions
   * have the same expiration date, given by a period from calibration time, and the same tenor.
   * 
   * @param bda  the business day adjustment for the exercise date adjustment
   * @param calibrationDateTime  the calibration date and time
   * @param dayCount  the day count for the computation of the time to exercise
   * @param periodToExpiry  the period to expiry
   * @param forward  the forward price/rate
   * @param strikesLike  the options strike-like dimension
   * @param strikeType  the strike type
   * @param prices  the option (call/payer) prices
   * @param startParameters  the starting parameters for the calibration. If one or more of the parameters are fixed,
   * the starting value will be used as the fixed parameter.
   * @param fixedParameters  the flag for the fixed parameters that are not calibrated
   * @param shiftOutput  the shift to calibrate the shifted SABR
   * @return SABR parameters
   */
  public SabrFormulaData calibrateShiftedFromPrices(
      BusinessDayAdjustment bda,
      ZonedDateTime calibrationDateTime,
      DayCount dayCount,
      Period periodToExpiry,
      double forward,
      DoubleArray strikesLike,
      ValueType strikeType,
      DoubleArray prices,
      DoubleArray startParameters, 
      BitSet fixedParameters,
      double shiftOutput) {
    int nbStrikes = strikesLike.size();
    ArgChecker.isTrue(nbStrikes == prices.size(), "size of strikes must be the same as size of prices");
    LocalDate calibrationDate = calibrationDateTime.toLocalDate();
    LocalDate exerciseDate = expirationDate(bda, calibrationDate, periodToExpiry);
    double timeToExpiry = dayCount.relativeYearFraction(calibrationDate, exerciseDate);
    double[] errors = new double[nbStrikes];
    Arrays.fill(errors, 1e-4);
    DoubleArray strikes = strikesShifted(forward, 0.0, strikesLike, strikeType);
    DoubleArray blackVolatilitiesTransformed = blackVolatilitiesShiftedFromPrices(
        forward, shiftOutput, timeToExpiry, strikes, prices);
    DoubleArray strikesShifted = strikesShifted(forward, shiftOutput, strikesLike, strikeType);
    SabrModelFitter fitter = new SabrModelFitter(forward + shiftOutput, strikesShifted, timeToExpiry,
        blackVolatilitiesTransformed, DoubleArray.ofUnsafe(errors), sabrFunctionProvider);
    LeastSquareResultsWithTransform r = fitter.solve(startParameters, fixedParameters);
    return SabrFormulaData.of(r.getModelParameters().toArrayUnsafe());
  }
  
  /**
   * Creates an array of shifted Black volatilities from option prices.
   * 
   * @param forward  the forward rate
   * @param shiftOutput  the shift required in the output
   * @param timeToExpiry  the time to expiration
   * @param strikes  the option strikes
   * @param prices  the option prices
   * @return the shifted black volatilities
   */
  public DoubleArray blackVolatilitiesShiftedFromPrices(
      double forward, 
      double shiftOutput, 
      double timeToExpiry, 
      DoubleArray strikes,
      DoubleArray prices) {
    int nbStrikes = strikes.size();
    double[] bv = new double[nbStrikes];
    for (int i = 0; i < nbStrikes; i++) {
      bv[i] = BlackFormulaRepository.impliedVolatility(
          prices.get(i), forward + shiftOutput, strikes.get(i) + shiftOutput, timeToExpiry, true);
    }
    return DoubleArray.ofUnsafe(bv);
  }

  /**
   * Calibrate the SABR parameters to a set of normal volatilities at given moneyness. All the associated swaptions
   * have the same expiration date, given by a period from calibration time, and the same tenor.
   * 
   * @param bda  the business day adjustment for the exercise date adjustment
   * @param calibrationDateTime  the calibration date and time
   * @param dayCount  the day count for the computation of the time to exercise
   * @param periodToExpiry  the period to expiry
   * @param forward  the forward price/rate
   * @param strikesLike  the options strike-like dimension
   * @param strikeType  the strike type
   * @param normalVolatilities  the option (call/payer) normal model implied volatilities
   * @param startParameters  the starting parameters for the calibration. If one or more of the parameters are fixed,
   * the starting value will be used as the fixed parameter.
   * @param fixedParameters  the flag for the fixed parameters that are not calibrated
   * @param shiftOutput  the shift to calibrate the shifted SABR
   * @return SABR parameters
   */
  public SabrFormulaData calibrateShiftedFromNormalVolatilities(
      BusinessDayAdjustment bda,
      ZonedDateTime calibrationDateTime,
      DayCount dayCount,
      Period periodToExpiry,
      double forward,
      DoubleArray strikesLike,
      ValueType strikeType,
      DoubleArray normalVolatilities,
      DoubleArray startParameters, 
      BitSet fixedParameters,
      double shiftOutput) {
    int nbStrikes = strikesLike.size();
    ArgChecker.isTrue(nbStrikes == normalVolatilities.size(), "size of strikes must be the same as size of prices");
    LocalDate calibrationDate = calibrationDateTime.toLocalDate();
    LocalDate exerciseDate = expirationDate(bda, calibrationDate, periodToExpiry);
    double timeToExpiry = dayCount.relativeYearFraction(calibrationDate, exerciseDate);
    double[] errors = new double[nbStrikes];
    Arrays.fill(errors, 1e-4);
    DoubleArray strikes = strikesShifted(forward, 0.0, strikesLike, strikeType);
    DoubleArray blackVolatilitiesTransformed = blackVolatilitiesShiftedFromNormalVolatilities(
        forward, shiftOutput, timeToExpiry, strikes, normalVolatilities);
    DoubleArray strikesShifted = strikesShifted(forward, shiftOutput, strikesLike, strikeType);
    SabrModelFitter fitter = new SabrModelFitter(forward + shiftOutput, strikesShifted, timeToExpiry,
        blackVolatilitiesTransformed, DoubleArray.ofUnsafe(errors), sabrFunctionProvider);
    LeastSquareResultsWithTransform r = fitter.solve(startParameters, fixedParameters);
    return SabrFormulaData.of(r.getModelParameters().toArrayUnsafe());
  }

  
  /**
   * Creates an array of shifted Black volatilities from Normal volatilities.
   * <p>
   * The transformation between normal and Black volatility is done using 
   * {@link BlackFormulaRepository#impliedVolatilityFromNormalApproximated}.
   * 
   * @param forward  the forward rate
   * @param shiftOutput  the shift required in the output
   * @param timeToExpiry  the time to expiration
   * @param strikes  the option strikes
   * @param normalVolatilities  the normal volatilities
   * @return the shifted black volatilities
   */
  public DoubleArray blackVolatilitiesShiftedFromNormalVolatilities(
      double forward, 
      double shiftOutput, 
      double timeToExpiry, 
      DoubleArray strikes,
      DoubleArray normalVolatilities) {
    int nbStrikes = strikes.size();
    double[] bv = new double[nbStrikes];
    for (int i = 0; i < nbStrikes; i++) {
      bv[i] = BlackFormulaRepository.impliedVolatilityFromNormalApproximated(
          forward + shiftOutput, strikes.get(i) + shiftOutput, timeToExpiry, normalVolatilities.get(i));
    }
    return DoubleArray.ofUnsafe(bv);
  }

  /**
   * Compute shifted strikes from forward and strike-like value type.
   * 
   * @param forward  the forward rate
   * @param shiftOutput  the shift for the output
   * @param strikesLike  the strike-like values 
   * @param strikeType  the strike type
   * @return the strikes
   */
  private DoubleArray strikesShifted(double forward, double shiftOutput, DoubleArray strikesLike, ValueType strikeType) {
    int nbStrikes = strikesLike.size();
    double[] strikes = new double[nbStrikes];
    if (strikeType.equals(ValueType.STRIKE)) {
      for (int i = 0; i < nbStrikes; i++) {
        strikes[i] = strikesLike.get(i) + shiftOutput;
      }
      return DoubleArray.ofUnsafe(strikes);
    }
    if (strikeType.equals(ValueType.SIMPLE_MONEYNESS)) {
      for (int i = 0; i < nbStrikes; i++) {
        strikes[i] = forward + strikesLike.get(i) + shiftOutput;
      }
      return DoubleArray.ofUnsafe(strikes);
    }
    if (strikeType.equals(ValueType.LOG_MONEYNESS)) {
      for (int i = 0; i < nbStrikes; i++) {
        strikes[i] = forward * strikesLike.get(i) + shiftOutput;
      }
      return DoubleArray.ofUnsafe(strikes);
    }
    throw new IllegalArgumentException("Strike type not supported")  ;
  }
  
  /**
   * Calculates the expiration date of a swaption from the calibration date and the underlying swap convention.
   * @param convention  the underlying swap convention
   * @param calibrationDate  the calibration date
   * @param expiry  the period to expiry
   * @return the date
   */
  private LocalDate expirationDate(
      BusinessDayAdjustment bda,
      LocalDate calibrationDate, 
      Period expiry) {
    return bda.adjust(calibrationDate.plus(expiry), REF_DATA);
  }

}