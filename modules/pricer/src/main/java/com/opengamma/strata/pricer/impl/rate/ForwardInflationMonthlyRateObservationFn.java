/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.rate;

import java.time.LocalDate;

import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.market.explain.ExplainKey;
import com.opengamma.strata.market.explain.ExplainMapBuilder;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.view.PriceIndexValues;
import com.opengamma.strata.pricer.rate.RateObservationFn;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.rate.InflationMonthlyRateObservation;

/**
 * Rate observation implementation for a price index. 
 * <p>
 * The pay-off for a unit notional is {@code (IndexEnd / IndexStart - 1)}, where
 * start index value and end index value are simply returned by {@code RatesProvider}.
 */
public class ForwardInflationMonthlyRateObservationFn
    implements RateObservationFn<InflationMonthlyRateObservation> {

  /**
   * Default instance.
   */
  public static final ForwardInflationMonthlyRateObservationFn DEFAULT =
      new ForwardInflationMonthlyRateObservationFn();

  /**
   * Creates an instance.
   */
  public ForwardInflationMonthlyRateObservationFn() {
  }

  //-------------------------------------------------------------------------
  @Override
  public double rate(
      InflationMonthlyRateObservation observation,
      LocalDate startDate,
      LocalDate endDate,
      RatesProvider provider) {

    PriceIndex index = observation.getIndex();
    PriceIndexValues values = provider.priceIndexValues(index);
    double indexStart = values.value(observation.getStartObservation());
    double indexEnd = values.value(observation.getEndObservation());
    return indexEnd / indexStart - 1d;
  }

  @Override
  public PointSensitivityBuilder rateSensitivity(
      InflationMonthlyRateObservation observation,
      LocalDate startDate,
      LocalDate endDate,
      RatesProvider provider) {

    PriceIndex index = observation.getIndex();
    PriceIndexValues values = provider.priceIndexValues(index);
    double indexStart = values.value(observation.getStartObservation());
    double indexEnd = values.value(observation.getEndObservation());
    double indexStartInv = 1d / indexStart;
    PointSensitivityBuilder sensi1 = values.valuePointSensitivity(observation.getStartObservation())
        .multipliedBy(-indexEnd * indexStartInv * indexStartInv);
    PointSensitivityBuilder sensi2 = values.valuePointSensitivity(observation.getEndObservation())
        .multipliedBy(indexStartInv);
    return sensi1.combinedWith(sensi2);
  }

  @Override
  public double explainRate(
      InflationMonthlyRateObservation observation,
      LocalDate startDate,
      LocalDate endDate,
      RatesProvider provider,
      ExplainMapBuilder builder) {

    PriceIndex index = observation.getIndex();
    PriceIndexValues values = provider.priceIndexValues(index);
    double indexStart = values.value(observation.getStartObservation());
    double indexEnd = values.value(observation.getEndObservation());

    builder.addListEntry(ExplainKey.OBSERVATIONS, child -> child
        .put(ExplainKey.ENTRY_TYPE, "InflationObservation")
        .put(ExplainKey.FIXING_DATE, observation.getStartObservation().getFixingMonth().atEndOfMonth())
        .put(ExplainKey.INDEX, index)
        .put(ExplainKey.INDEX_VALUE, indexStart));
    builder.addListEntry(ExplainKey.OBSERVATIONS, child -> child
        .put(ExplainKey.ENTRY_TYPE, "InflationObservation")
        .put(ExplainKey.FIXING_DATE, observation.getEndObservation().getFixingMonth().atEndOfMonth())
        .put(ExplainKey.INDEX, index)
        .put(ExplainKey.INDEX_VALUE, indexEnd));
    double rate = rate(observation, startDate, endDate, provider);
    builder.put(ExplainKey.COMBINED_RATE, rate);
    return rate;
  }

}
