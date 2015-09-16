/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.DiscountIborIndexRates;
import com.opengamma.strata.market.value.DiscountOvernightIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;

/**
 * Builder for the immutable rates provider.
 * 
 * @see ImmutableRatesProvider
 */
public final class ImmutableRatesProviderBuilder {

  /**
   * The valuation date.
   */
  private final LocalDate valuationDate;
  /**
   * The matrix of foreign exchange rates, defaulted to an empty matrix.
   */
  private FxMatrix fxMatrix = FxMatrix.empty();
  /**
   * The discount factors.
   */
  private Map<Currency, DiscountFactors> discountFactors = new HashMap<>();
  /**
   * The Ibor forward curves.
   */
  private Map<IborIndex, IborIndexRates> iborIndexRates = new HashMap<>();
  /**
   * The Overnight forward curves.
   */
  private Map<OvernightIndex, OvernightIndexRates> overnightIndexRates = new HashMap<>();
  /**
   * The price index values.
   */
  private Map<PriceIndex, PriceIndexValues> priceIndexValues = new HashMap<>();
  /**
   * The time-series for FX indices.
   */
  private Map<FxIndex, LocalDateDoubleTimeSeries> fxIndexTimeSeries = new HashMap<>();

  //-------------------------------------------------------------------------
  /**
   * Creates an instance specifying the valuation date.
   * 
   * @param valuationDate  the valuation date
   */
  ImmutableRatesProviderBuilder(LocalDate valuationDate) {
    this.valuationDate = ArgChecker.notNull(valuationDate, "valuationDate");
  }

  //-------------------------------------------------------------------------
  /**
   * Sets the FX matrix.
   * 
   * @param fxMatrix  the matrix
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder fxMatrix(FxMatrix fxMatrix) {
    this.fxMatrix = ArgChecker.notNull(fxMatrix, "fxMatrix");
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds discount factors to the provider.
   * <p>
   * This adds the specified discount factors to the provider.
   * The valuation date of the discount factors must match the valuation date of the builder.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param discountFactors  the discount factors
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder discountFactors(DiscountFactors... discountFactors) {
    ArgChecker.notNull(discountFactors, "discountFactors");
    for (DiscountFactors df : discountFactors) {
      checkValuationDate(df.getValuationDate());
      this.discountFactors.put(df.getCurrency(), df);
    }
    return this;
  }

  /**
   * Adds discount factors to the provider.
   * <p>
   * This adds the specified discount factors to the provider.
   * The valuation date of the discount factors must match the valuation date of the builder.
   * This operates using {@link Map#putAll(Map)} semantics using the currency as the key.
   * 
   * @param discountFactors  the discount factors
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder discountFactors(Map<Currency, DiscountFactors> discountFactors) {
    ArgChecker.notNull(discountFactors, "discountFactors");
    for (DiscountFactors df : discountFactors.values()) {
      checkValuationDate(df.getValuationDate());
      this.discountFactors.put(df.getCurrency(), df);
    }
    return this;
  }

  /**
   * Adds a discount curve to the provider.
   * <p>
   * This adds the specified discount curve to the provider.
   * The valuation date and object type will be derived from the curve.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param currency  the currency of the curve
   * @param discountCurve  the discount curve
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder discountCurve(Currency currency, Curve discountCurve) {
    ArgChecker.notNull(currency, "currency");
    ArgChecker.notNull(discountCurve, "discountCurve");
    this.discountFactors.put(currency, DiscountFactors.of(currency, valuationDate, discountCurve));
    return this;
  }

  /**
   * Adds discount curves to the provider.
   * <p>
   * This adds the specified discount curves to the provider.
   * This operates using {@link Map#putAll(Map)} semantics using the currency as the key.
   * 
   * @param discountCurves  the discount curves
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder discountCurves(Map<Currency, Curve> discountCurves) {
    ArgChecker.notNull(discountCurves, "discountCurves");
    for (Entry<Currency, Curve> entry : discountCurves.entrySet()) {
      discountCurve(entry.getKey(), entry.getValue());
    }
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds Ibor index values to the provider.
   * <p>
   * This adds the specified Ibor index values to the provider.
   * The valuation date of the Ibor index values must match the valuation date of the builder.
   * This operates using {@link Map#put(Object, Object)} semantics using the index as the key.
   * 
   * @param iborIndexRates  the Ibor index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder iborIndexRates(IborIndexRates... iborIndexRates) {
    ArgChecker.notNull(iborIndexRates, "iborIndexRates");
    for (IborIndexRates rates : iborIndexRates) {
      checkValuationDate(rates.getValuationDate());
      this.iborIndexRates.put(rates.getIndex(), rates);
    }
    return this;
  }

  /**
   * Adds Ibor index values to the provider.
   * <p>
   * This adds the specified Ibor index values to the provider.
   * The valuation date of the Ibor index values must match the valuation date of the builder.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param iborIndexRates  the Ibor index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder iborIndexRates(Map<IborIndex, IborIndexRates> iborIndexRates) {
    ArgChecker.notNull(iborIndexRates, "iborIndexRates");
    for (IborIndexRates rates : iborIndexRates.values()) {
      checkValuationDate(rates.getValuationDate());
      this.iborIndexRates.put(rates.getIndex(), rates);
    }
    return this;
  }

  /**
   * Adds an Ibor index forward curve to the provider, retaining any existing time-series.
   * <p>
   * This adds the specified index forward curve to the provider.
   * The valuation date and object type will be derived from the curve.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param index  the index of the curve
   * @param forwardCurve  the Ibor index forward curve
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder iborIndexCurve(IborIndex index, Curve forwardCurve) {
    ArgChecker.notNull(index, "currency");
    ArgChecker.notNull(forwardCurve, "forwardCurve");
    IborIndexRates existing = iborIndexRates.get(index);
    if (existing != null) {
      this.iborIndexRates.put(index, existing.applyPerturbation(curve -> forwardCurve));
      return this;
    }
    return iborIndexCurve(index, forwardCurve, LocalDateDoubleTimeSeries.empty());
  }

  /**
   * Adds a index forward curve to the provider with associated time-series.
   * <p>
   * This adds the specified index forward curve to the provider.
   * The valuation date and object type will be derived from the curve.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param index  the index of the curve
   * @param forwardCurve  the index forward curve
   * @param timeSeries  the associated time-series
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder iborIndexCurve(
      IborIndex index,
      Curve forwardCurve,
      LocalDateDoubleTimeSeries timeSeries) {

    ArgChecker.notNull(index, "currency");
    ArgChecker.notNull(forwardCurve, "forwardCurve");
    ArgChecker.notNull(timeSeries, "timeSeries");
    ZeroRateDiscountFactors df = ZeroRateDiscountFactors.of(index.getCurrency(), valuationDate, forwardCurve);
    this.iborIndexRates.put(index, DiscountIborIndexRates.of(index, timeSeries, df));
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds Overnight index values to the provider.
   * <p>
   * This adds the specified Overnight index values to the provider.
   * The valuation date of the Overnight index values must match the valuation date of the builder.
   * This operates using {@link Map#put(Object, Object)} semantics using the index as the key.
   * 
   * @param overnightIndexRates  the Overnight index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder overnightIndexRates(OvernightIndexRates... overnightIndexRates) {
    ArgChecker.notNull(overnightIndexRates, "overnightIndexRates");
    for (OvernightIndexRates rates : overnightIndexRates) {
      checkValuationDate(rates.getValuationDate());
      this.overnightIndexRates.put(rates.getIndex(), rates);
    }
    return this;
  }

  /**
   * Adds Overnight index values to the provider.
   * <p>
   * This adds the specified Overnight index values to the provider.
   * The valuation date of the Overnight index values must match the valuation date of the builder.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param overnightIndexRates  the Overnight index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder overnightIndexRates(Map<OvernightIndex, OvernightIndexRates> overnightIndexRates) {
    ArgChecker.notNull(overnightIndexRates, "overnightIndexRates");
    for (OvernightIndexRates rates : overnightIndexRates.values()) {
      checkValuationDate(rates.getValuationDate());
      this.overnightIndexRates.put(rates.getIndex(), rates);
    }
    return this;
  }

  /**
   * Adds an Overnight index forward curve to the provider, retaining any existing time-series.
   * <p>
   * This adds the specified index forward curve to the provider.
   * The valuation date and object type will be derived from the curve.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param index  the index of the curve
   * @param forwardCurve  the Overnight index forward curve
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder overnightIndexCurve(OvernightIndex index, Curve forwardCurve) {
    ArgChecker.notNull(index, "currency");
    ArgChecker.notNull(forwardCurve, "forwardCurve");
    OvernightIndexRates existing = overnightIndexRates.get(index);
    if (existing != null) {
      this.overnightIndexRates.put(index, existing.applyPerturbation(curve -> forwardCurve));
      return this;
    }
    return overnightIndexCurve(index, forwardCurve, LocalDateDoubleTimeSeries.empty());
  }

  /**
   * Adds a index forward curve to the provider with associated time-series.
   * <p>
   * This adds the specified index forward curve to the provider.
   * The valuation date and object type will be derived from the curve.
   * This operates using {@link Map#put(Object, Object)} semantics using the currency as the key.
   * 
   * @param index  the index of the curve
   * @param forwardCurve  the index forward curve
   * @param timeSeries  the associated time-series
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder overnightIndexCurve(
      OvernightIndex index,
      Curve forwardCurve,
      LocalDateDoubleTimeSeries timeSeries) {

    ArgChecker.notNull(index, "currency");
    ArgChecker.notNull(forwardCurve, "forwardCurve");
    ArgChecker.notNull(timeSeries, "timeSeries");
    ZeroRateDiscountFactors df = ZeroRateDiscountFactors.of(index.getCurrency(), valuationDate, forwardCurve);
    this.overnightIndexRates.put(index, DiscountOvernightIndexRates.of(index, timeSeries, df));
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds index forward curves to the provider, retaining any existing time-series.
   * <p>
   * This adds the specified index forward curves to the provider.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param indexCurves  the index forward curves
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder indexCurves(Map<Index, Curve> indexCurves) {
    ArgChecker.noNulls(indexCurves, "indexCurves");
    for (Entry<Index, Curve> entry : indexCurves.entrySet()) {
      Index index = entry.getKey();
      if (index instanceof IborIndex) {
        iborIndexCurve((IborIndex) index, entry.getValue());
      } else if (index instanceof OvernightIndex) {
        overnightIndexCurve((OvernightIndex) index, entry.getValue());
      } else {
        throw new IllegalArgumentException("Unknown index type: " + index);
      }
    }
    return this;
  }

  /**
   * Adds index forward curves to the provider with associated time-series.
   * <p>
   * This adds the specified index forward curves to the provider.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param indexCurves  the index forward curves
   * @param timeSeries  the associated time-series
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder indexCurves(
      Map<Index, Curve> indexCurves,
      Map<Index, LocalDateDoubleTimeSeries> timeSeries) {

    ArgChecker.noNulls(indexCurves, "indexCurves");
    for (Entry<Index, Curve> entry : indexCurves.entrySet()) {
      Index index = entry.getKey();
      LocalDateDoubleTimeSeries ts = timeSeries.get(index);
      ts = (ts != null ? ts : LocalDateDoubleTimeSeries.empty());
      if (index instanceof IborIndex) {
        iborIndexCurve((IborIndex) index, entry.getValue(), ts);
      } else if (index instanceof OvernightIndex) {
        overnightIndexCurve((OvernightIndex) index, entry.getValue(), ts);
      } else {
        throw new IllegalArgumentException("Unknown index type: " + index);
      }
    }
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds price index values to the provider.
   * <p>
   * This adds the specified price index values to the provider.
   * The valuation date of the price index values must match the valuation date of the builder.
   * This operates using {@link Map#put(Object, Object)} semantics using the index as the key.
   * 
   * @param priceIndexValues  the price index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder priceIndexValues(PriceIndexValues... priceIndexValues) {
    ArgChecker.notNull(priceIndexValues, "priceIndexValues");
    for (PriceIndexValues piv : priceIndexValues) {
      ArgChecker.isTrue(
          YearMonth.from(valuationDate).equals(piv.getValuationMonth()),
          "Valuation date differs, {} and {}", valuationDate, piv.getValuationMonth());
      this.priceIndexValues.put(piv.getIndex(), piv);
    }
    return this;
  }

  /**
   * Adds price index values to the provider.
   * <p>
   * This adds the specified price index values to the provider.
   * The valuation date of the price index values must match the valuation date of the builder.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param priceIndexValues  the price index values
   * @return this, for chaining
   * @throws IllegalArgumentException if the valuation date does not match
   */
  public ImmutableRatesProviderBuilder priceIndexValues(Map<PriceIndex, PriceIndexValues> priceIndexValues) {
    ArgChecker.notNull(priceIndexValues, "priceIndexValues");
    for (PriceIndexValues piv : priceIndexValues.values()) {
      ArgChecker.isTrue(
          YearMonth.from(valuationDate).equals(piv.getValuationMonth()),
          "Valuation date differs, {} and {}", valuationDate, piv.getValuationMonth());
      this.priceIndexValues.put(piv.getIndex(), piv);
    }
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Adds FX index time-series to the provider.
   * <p>
   * This adds the specified time-series to the provider.
   * This operates using {@link Map#put(Object, Object)} semantics using the index as the key.
   * 
   * @param index  the FX index
   * @param timeSeries  the FX index time-series
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder fxIndexTimeSeries(FxIndex index, LocalDateDoubleTimeSeries timeSeries) {
    ArgChecker.notNull(index, "index");
    ArgChecker.notNull(timeSeries, "timeSeries");
    this.fxIndexTimeSeries.put(index, timeSeries);
    return this;
  }

  /**
   * Adds FX index time-series to the provider.
   * <p>
   * This adds the specified time-series to the provider.
   * This operates using {@link Map#putAll(Map)} semantics using the index as the key.
   * 
   * @param timeSeries  the FX index time-series
   * @return this, for chaining
   */
  public ImmutableRatesProviderBuilder fxIndexTimeSeries(Map<FxIndex, LocalDateDoubleTimeSeries> timeSeries) {
    ArgChecker.noNulls(timeSeries, "timeSeries");
    this.fxIndexTimeSeries.putAll(timeSeries);
    return this;
  }

  //-------------------------------------------------------------------------
  /**
   * Completes the builder, returning the provider.
   * 
   * @return the provider
   */
  public ImmutableRatesProvider build() {
    return new ImmutableRatesProvider(
        valuationDate,
        fxMatrix,
        discountFactors,
        iborIndexRates,
        overnightIndexRates,
        priceIndexValues,
        fxIndexTimeSeries);
  }

  //-------------------------------------------------------------------------
  private void checkValuationDate(LocalDate inputValuationDate) {
    ArgChecker.isTrue(
        valuationDate.equals(inputValuationDate),
        "Valuation date differs, {} and {}", valuationDate, inputValuationDate);
  }

}
