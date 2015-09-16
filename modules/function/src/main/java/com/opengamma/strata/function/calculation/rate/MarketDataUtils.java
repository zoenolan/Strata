/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.calculation.rate;

import java.time.LocalDate;
import java.util.Set;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.engine.marketdata.SingleCalculationMarketData;
import com.opengamma.strata.function.marketdata.MarketDataRatesProvider;
import com.opengamma.strata.market.key.IndexRateKey;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.DiscountIborIndexRates;
import com.opengamma.strata.market.value.DiscountOvernightIndexRates;
import com.opengamma.strata.market.value.FxForwardRates;
import com.opengamma.strata.market.value.FxIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.AbstractRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * Utilities for manipulating market data.
 */
public final class MarketDataUtils {

  /**
   * Restricted constructor.
   */
  private MarketDataUtils() {
  }

  //-------------------------------------------------------------------------
  /**
   * Creates a rates provider from a set of market data containing a single discounting curve,
   * and forward curves and fixing series for a given set of indices.
   * All matching curves are overridden by the replacement. 
   * 
   * @param marketData  the market data
   * @param indicesToOverride  the indices
   * @param overrideDf  the overriding discount factors
   * @return the rates provider
   */
  public static RatesProvider toSingleCurveRatesProvider(
      SingleCalculationMarketData marketData,
      Set<? extends Index> indicesToOverride,
      ZeroRateDiscountFactors overrideDf) {

    MarketDataRatesProvider base = new MarketDataRatesProvider(marketData);
    return new AbstractRatesProvider() {
      @Override
      public LocalDate getValuationDate() {
        return marketData.getValuationDate();
      }

      @Override
      public <T> T data(MarketDataKey<T> key) {
        return base.data(key);
      }

      @Override
      public double fxRate(Currency baseCurrency, Currency counterCurrency) {
        return base.fxRate(baseCurrency, counterCurrency);
      }

      @Override
      public DiscountFactors discountFactors(Currency currency) {
        if (overrideDf.getCurrency().equals(currency)) {
          return overrideDf;
        }
        return base.discountFactors(currency);
      }

      @Override
      public FxIndexRates fxIndexRates(FxIndex index) {
        return base.fxIndexRates(index);
      }

      @Override
      public FxForwardRates fxForwardRates(CurrencyPair currencyPair) {
        return base.fxForwardRates(currencyPair);
      }

      @Override
      public IborIndexRates iborIndexRates(IborIndex index) {
        if (indicesToOverride.contains(index)) {
          LocalDateDoubleTimeSeries timeSeries = marketData.getTimeSeries(IndexRateKey.of(index));
          return DiscountIborIndexRates.of(index, timeSeries, overrideDf);
        }
        return base.iborIndexRates(index);
      }

      @Override
      public OvernightIndexRates overnightIndexRates(OvernightIndex index) {
        if (indicesToOverride.contains(index)) {
          LocalDateDoubleTimeSeries timeSeries = marketData.getTimeSeries(IndexRateKey.of(index));
          return DiscountOvernightIndexRates.of(index, timeSeries, overrideDf);
        }
        return base.overnightIndexRates(index);
      }

      @Override
      public PriceIndexValues priceIndexValues(PriceIndex index) {
        return base.priceIndexValues(index);
      }
    };
  }

}
