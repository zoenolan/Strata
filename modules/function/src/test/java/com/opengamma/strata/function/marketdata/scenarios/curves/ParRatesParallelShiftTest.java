/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.function.marketdata.scenarios.curves;

import static com.opengamma.strata.collect.CollectProjectAssertions.assertThat;

import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.ParRates;
import com.opengamma.strata.market.id.QuoteId;

@Test
public class ParRatesParallelShiftTest {

  private static final String SCHEME = "test";

  public void absolute() {
    Map<QuoteId, Double> rates = ImmutableMap.of(
        QuoteId.of(StandardId.of(SCHEME, "1")), 1d,
        QuoteId.of(StandardId.of(SCHEME, "2")), 2d,
        QuoteId.of(StandardId.of(SCHEME, "4")), 4d);

    ParRates parRates = ParRates.of(rates, CurveMetadata.of("curve"));
    ParRatesParallelShift shift = ParRatesParallelShift.absolute(0.1);
    ParRates shiftedParRates = shift.apply(parRates);

    Map<QuoteId, Double> expectedRates = ImmutableMap.of(
        QuoteId.of(StandardId.of(SCHEME, "1")), 1.1,
        QuoteId.of(StandardId.of(SCHEME, "2")), 2.1,
        QuoteId.of(StandardId.of(SCHEME, "4")), 4.1);

    assertThat(shiftedParRates.getRates()).isEqualTo(expectedRates);
  }

  public void relative() {
    Map<QuoteId, Double> rates = ImmutableMap.of(
        QuoteId.of(StandardId.of(SCHEME, "1")), 1d,
        QuoteId.of(StandardId.of(SCHEME, "2")), 2d,
        QuoteId.of(StandardId.of(SCHEME, "4")), 4d);

    ParRates parRates = ParRates.of(rates, CurveMetadata.of("curve"));
    ParRatesParallelShift shift = ParRatesParallelShift.relative(0.1);
    ParRates shiftedParRates = shift.apply(parRates);

    Map<QuoteId, Double> expectedRates = ImmutableMap.of(
        QuoteId.of(StandardId.of(SCHEME, "1")), 1.1,
        QuoteId.of(StandardId.of(SCHEME, "2")), 2.2,
        QuoteId.of(StandardId.of(SCHEME, "4")), 4.4);

    assertThat(shiftedParRates.getRates()).isEqualTo(expectedRates);
  }
}
