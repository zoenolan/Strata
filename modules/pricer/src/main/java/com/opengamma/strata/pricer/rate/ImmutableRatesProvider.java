/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableDefaults;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.DiscountFxForwardRates;
import com.opengamma.strata.market.value.DiscountFxIndexRates;
import com.opengamma.strata.market.value.FxForwardRates;
import com.opengamma.strata.market.value.FxIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.PriceIndexValues;

/**
 * The default immutable rates provider, used to calculate analytic measures.
 * <p>
 * This provides the environmental information against which pricing occurs.
 * This includes FX rates, discount factors and forward curves.
 */
@BeanDefinition(builderScope = "private", constructorScope = "package")
public final class ImmutableRatesProvider
    extends AbstractRatesProvider
    implements ImmutableBean, Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1L;

  /**
   * The valuation date.
   * All curves and other data items in this provider are calibrated for this date.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final LocalDate valuationDate;
  /**
   * The matrix of foreign exchange rates, defaulted to an empty matrix.
   */
  @PropertyDefinition(validate = "notNull", get = "private")
  private final FxMatrix fxMatrix;
  /**
   * The discount factors, defaulted to an empty map.
   * The curve data, predicting the future, associated with each currency.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<Currency, DiscountFactors> discountFactors;
  /**
   * The Ibor index forward rates, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<IborIndex, IborIndexRates> iborIndexRates;
  /**
   * The Overnight index forward rates, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<OvernightIndex, OvernightIndexRates> overnightIndexRates;
  /**
   * The price index values, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<PriceIndex, PriceIndexValues> priceIndexValues;
  /**
   * The FX index time-series, defaulted to an empty map.
   * The historic data associated with each index.
   */
  @PropertyDefinition(validate = "notNull", get = "private")
  private final ImmutableMap<FxIndex, LocalDateDoubleTimeSeries> fxIndexTimeSeries;

  //-------------------------------------------------------------------------
  @ImmutableDefaults
  private static void applyDefaults(Builder builder) {
    builder.fxMatrix = FxMatrix.empty();
  }

  //-------------------------------------------------------------------------
  /**
   * Creates a builder specifying the valuation date.
   * 
   * @param valuationDate  the valuation date
   * @return the builder
   */
  public static ImmutableRatesProviderBuilder builder(LocalDate valuationDate) {
    return new ImmutableRatesProviderBuilder(valuationDate);
  }

  /**
   * Converts this instance to a builder allowing changes to be made.
   * 
   * @return the builder
   */
  public ImmutableRatesProviderBuilder toBuilder() {
    return toBuilder(valuationDate);
  }

  /**
   * Converts this instance to a builder allowing changes to be made.
   * <p>
   * This overload allows the valuation date to be altered.
   * 
   * @param valuationDate  the new valuation date
   * @return the builder
   */
  public ImmutableRatesProviderBuilder toBuilder(LocalDate valuationDate) {
    return new ImmutableRatesProviderBuilder(valuationDate)
        .fxMatrix(fxMatrix)
        .discountFactors(discountFactors)
        .iborIndexRates(iborIndexRates)
        .overnightIndexRates(overnightIndexRates)
        .priceIndexValues(priceIndexValues)
        .fxIndexTimeSeries(fxIndexTimeSeries);
  }

  //-------------------------------------------------------------------------
  @Override
  public <T> T data(MarketDataKey<T> key) {
    throw new IllegalArgumentException("Unknown key: " + key.toString());
  }

  //-------------------------------------------------------------------------
  @Override
  public double fxRate(Currency baseCurrency, Currency counterCurrency) {
    ArgChecker.notNull(baseCurrency, "baseCurrency");
    ArgChecker.notNull(counterCurrency, "counterCurrency");
    if (baseCurrency.equals(counterCurrency)) {
      return 1d;
    }
    return fxMatrix.fxRate(baseCurrency, counterCurrency);
  }

  //-------------------------------------------------------------------------
  @Override
  public DiscountFactors discountFactors(Currency currency) {
    DiscountFactors df = discountFactors.get(currency);
    if (df == null) {
      throw new IllegalArgumentException("Unable to find discount curve: " + currency);
    }
    return df;
  }

  //-------------------------------------------------------------------------
  @Override
  public FxIndexRates fxIndexRates(FxIndex index) {
    LocalDateDoubleTimeSeries timeSeries = fxIndexTimeSeries.get(index);
    if (timeSeries == null) {
      throw new IllegalArgumentException("Unknown index: " + index.getName());
    }
    FxForwardRates fxForwardRates = fxForwardRates(index.getCurrencyPair());
    return DiscountFxIndexRates.of(index, timeSeries, fxForwardRates);
  }

  //-------------------------------------------------------------------------
  @Override
  public FxForwardRates fxForwardRates(CurrencyPair currencyPair) {
    DiscountFactors base = discountFactors(currencyPair.getBase());
    DiscountFactors counter = discountFactors(currencyPair.getCounter());
    return DiscountFxForwardRates.of(currencyPair, fxMatrix, base, counter);
  };

  //-------------------------------------------------------------------------
  @Override
  public IborIndexRates iborIndexRates(IborIndex index) {
    IborIndexRates rates = iborIndexRates.get(index);
    if (rates == null) {
      throw new IllegalArgumentException("Unable to find Ibor forward curve: " + index);
    }
    return rates;
  }

  @Override
  public OvernightIndexRates overnightIndexRates(OvernightIndex index) {
    OvernightIndexRates rates = overnightIndexRates.get(index);
    if (rates == null) {
      throw new IllegalArgumentException("Unable to find Overnight forward curve: " + index);
    }
    return rates;
  }

  @Override
  public PriceIndexValues priceIndexValues(PriceIndex index) {
    PriceIndexValues values = priceIndexValues.get(index);
    if (values == null) {
      throw new IllegalArgumentException("Unable to find index: " + index);
    }
    return values;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutableRatesProvider}.
   * @return the meta-bean, not null
   */
  public static ImmutableRatesProvider.Meta meta() {
    return ImmutableRatesProvider.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutableRatesProvider.Meta.INSTANCE);
  }

  /**
   * Creates an instance.
   * @param valuationDate  the value of the property, not null
   * @param fxMatrix  the value of the property, not null
   * @param discountFactors  the value of the property, not null
   * @param iborIndexRates  the value of the property, not null
   * @param overnightIndexRates  the value of the property, not null
   * @param priceIndexValues  the value of the property, not null
   * @param fxIndexTimeSeries  the value of the property, not null
   */
  ImmutableRatesProvider(
      LocalDate valuationDate,
      FxMatrix fxMatrix,
      Map<Currency, DiscountFactors> discountFactors,
      Map<IborIndex, IborIndexRates> iborIndexRates,
      Map<OvernightIndex, OvernightIndexRates> overnightIndexRates,
      Map<PriceIndex, PriceIndexValues> priceIndexValues,
      Map<FxIndex, LocalDateDoubleTimeSeries> fxIndexTimeSeries) {
    JodaBeanUtils.notNull(valuationDate, "valuationDate");
    JodaBeanUtils.notNull(fxMatrix, "fxMatrix");
    JodaBeanUtils.notNull(discountFactors, "discountFactors");
    JodaBeanUtils.notNull(iborIndexRates, "iborIndexRates");
    JodaBeanUtils.notNull(overnightIndexRates, "overnightIndexRates");
    JodaBeanUtils.notNull(priceIndexValues, "priceIndexValues");
    JodaBeanUtils.notNull(fxIndexTimeSeries, "fxIndexTimeSeries");
    this.valuationDate = valuationDate;
    this.fxMatrix = fxMatrix;
    this.discountFactors = ImmutableMap.copyOf(discountFactors);
    this.iborIndexRates = ImmutableMap.copyOf(iborIndexRates);
    this.overnightIndexRates = ImmutableMap.copyOf(overnightIndexRates);
    this.priceIndexValues = ImmutableMap.copyOf(priceIndexValues);
    this.fxIndexTimeSeries = ImmutableMap.copyOf(fxIndexTimeSeries);
  }

  @Override
  public ImmutableRatesProvider.Meta metaBean() {
    return ImmutableRatesProvider.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation date.
   * All curves and other data items in this provider are calibrated for this date.
   * @return the value of the property, not null
   */
  @Override
  public LocalDate getValuationDate() {
    return valuationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the matrix of foreign exchange rates, defaulted to an empty matrix.
   * @return the value of the property, not null
   */
  private FxMatrix getFxMatrix() {
    return fxMatrix;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the discount factors, defaulted to an empty map.
   * The curve data, predicting the future, associated with each currency.
   * @return the value of the property, not null
   */
  public ImmutableMap<Currency, DiscountFactors> getDiscountFactors() {
    return discountFactors;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Ibor index forward rates, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   * @return the value of the property, not null
   */
  public ImmutableMap<IborIndex, IborIndexRates> getIborIndexRates() {
    return iborIndexRates;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Overnight index forward rates, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   * @return the value of the property, not null
   */
  public ImmutableMap<OvernightIndex, OvernightIndexRates> getOvernightIndexRates() {
    return overnightIndexRates;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the price index values, defaulted to an empty map.
   * The curve data, predicting the future, associated with each index.
   * @return the value of the property, not null
   */
  public ImmutableMap<PriceIndex, PriceIndexValues> getPriceIndexValues() {
    return priceIndexValues;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the FX index time-series, defaulted to an empty map.
   * The historic data associated with each index.
   * @return the value of the property, not null
   */
  private ImmutableMap<FxIndex, LocalDateDoubleTimeSeries> getFxIndexTimeSeries() {
    return fxIndexTimeSeries;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ImmutableRatesProvider other = (ImmutableRatesProvider) obj;
      return JodaBeanUtils.equal(getValuationDate(), other.getValuationDate()) &&
          JodaBeanUtils.equal(getFxMatrix(), other.getFxMatrix()) &&
          JodaBeanUtils.equal(getDiscountFactors(), other.getDiscountFactors()) &&
          JodaBeanUtils.equal(getIborIndexRates(), other.getIborIndexRates()) &&
          JodaBeanUtils.equal(getOvernightIndexRates(), other.getOvernightIndexRates()) &&
          JodaBeanUtils.equal(getPriceIndexValues(), other.getPriceIndexValues()) &&
          JodaBeanUtils.equal(getFxIndexTimeSeries(), other.getFxIndexTimeSeries());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getValuationDate());
    hash = hash * 31 + JodaBeanUtils.hashCode(getFxMatrix());
    hash = hash * 31 + JodaBeanUtils.hashCode(getDiscountFactors());
    hash = hash * 31 + JodaBeanUtils.hashCode(getIborIndexRates());
    hash = hash * 31 + JodaBeanUtils.hashCode(getOvernightIndexRates());
    hash = hash * 31 + JodaBeanUtils.hashCode(getPriceIndexValues());
    hash = hash * 31 + JodaBeanUtils.hashCode(getFxIndexTimeSeries());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(256);
    buf.append("ImmutableRatesProvider{");
    buf.append("valuationDate").append('=').append(getValuationDate()).append(',').append(' ');
    buf.append("fxMatrix").append('=').append(getFxMatrix()).append(',').append(' ');
    buf.append("discountFactors").append('=').append(getDiscountFactors()).append(',').append(' ');
    buf.append("iborIndexRates").append('=').append(getIborIndexRates()).append(',').append(' ');
    buf.append("overnightIndexRates").append('=').append(getOvernightIndexRates()).append(',').append(' ');
    buf.append("priceIndexValues").append('=').append(getPriceIndexValues()).append(',').append(' ');
    buf.append("fxIndexTimeSeries").append('=').append(JodaBeanUtils.toString(getFxIndexTimeSeries()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableRatesProvider}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", ImmutableRatesProvider.class, LocalDate.class);
    /**
     * The meta-property for the {@code fxMatrix} property.
     */
    private final MetaProperty<FxMatrix> fxMatrix = DirectMetaProperty.ofImmutable(
        this, "fxMatrix", ImmutableRatesProvider.class, FxMatrix.class);
    /**
     * The meta-property for the {@code discountFactors} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<Currency, DiscountFactors>> discountFactors = DirectMetaProperty.ofImmutable(
        this, "discountFactors", ImmutableRatesProvider.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code iborIndexRates} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<IborIndex, IborIndexRates>> iborIndexRates = DirectMetaProperty.ofImmutable(
        this, "iborIndexRates", ImmutableRatesProvider.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code overnightIndexRates} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<OvernightIndex, OvernightIndexRates>> overnightIndexRates = DirectMetaProperty.ofImmutable(
        this, "overnightIndexRates", ImmutableRatesProvider.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code priceIndexValues} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<PriceIndex, PriceIndexValues>> priceIndexValues = DirectMetaProperty.ofImmutable(
        this, "priceIndexValues", ImmutableRatesProvider.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code fxIndexTimeSeries} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<FxIndex, LocalDateDoubleTimeSeries>> fxIndexTimeSeries = DirectMetaProperty.ofImmutable(
        this, "fxIndexTimeSeries", ImmutableRatesProvider.class, (Class) ImmutableMap.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "valuationDate",
        "fxMatrix",
        "discountFactors",
        "iborIndexRates",
        "overnightIndexRates",
        "priceIndexValues",
        "fxIndexTimeSeries");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 113107279:  // valuationDate
          return valuationDate;
        case -1198118093:  // fxMatrix
          return fxMatrix;
        case -91613053:  // discountFactors
          return discountFactors;
        case 949568445:  // iborIndexRates
          return iborIndexRates;
        case 2138434757:  // overnightIndexRates
          return overnightIndexRates;
        case 1422773131:  // priceIndexValues
          return priceIndexValues;
        case 833356516:  // fxIndexTimeSeries
          return fxIndexTimeSeries;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ImmutableRatesProvider> builder() {
      return new ImmutableRatesProvider.Builder();
    }

    @Override
    public Class<? extends ImmutableRatesProvider> beanType() {
      return ImmutableRatesProvider.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> valuationDate() {
      return valuationDate;
    }

    /**
     * The meta-property for the {@code fxMatrix} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FxMatrix> fxMatrix() {
      return fxMatrix;
    }

    /**
     * The meta-property for the {@code discountFactors} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<Currency, DiscountFactors>> discountFactors() {
      return discountFactors;
    }

    /**
     * The meta-property for the {@code iborIndexRates} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<IborIndex, IborIndexRates>> iborIndexRates() {
      return iborIndexRates;
    }

    /**
     * The meta-property for the {@code overnightIndexRates} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<OvernightIndex, OvernightIndexRates>> overnightIndexRates() {
      return overnightIndexRates;
    }

    /**
     * The meta-property for the {@code priceIndexValues} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<PriceIndex, PriceIndexValues>> priceIndexValues() {
      return priceIndexValues;
    }

    /**
     * The meta-property for the {@code fxIndexTimeSeries} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<FxIndex, LocalDateDoubleTimeSeries>> fxIndexTimeSeries() {
      return fxIndexTimeSeries;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 113107279:  // valuationDate
          return ((ImmutableRatesProvider) bean).getValuationDate();
        case -1198118093:  // fxMatrix
          return ((ImmutableRatesProvider) bean).getFxMatrix();
        case -91613053:  // discountFactors
          return ((ImmutableRatesProvider) bean).getDiscountFactors();
        case 949568445:  // iborIndexRates
          return ((ImmutableRatesProvider) bean).getIborIndexRates();
        case 2138434757:  // overnightIndexRates
          return ((ImmutableRatesProvider) bean).getOvernightIndexRates();
        case 1422773131:  // priceIndexValues
          return ((ImmutableRatesProvider) bean).getPriceIndexValues();
        case 833356516:  // fxIndexTimeSeries
          return ((ImmutableRatesProvider) bean).getFxIndexTimeSeries();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code ImmutableRatesProvider}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<ImmutableRatesProvider> {

    private LocalDate valuationDate;
    private FxMatrix fxMatrix;
    private Map<Currency, DiscountFactors> discountFactors = ImmutableMap.of();
    private Map<IborIndex, IborIndexRates> iborIndexRates = ImmutableMap.of();
    private Map<OvernightIndex, OvernightIndexRates> overnightIndexRates = ImmutableMap.of();
    private Map<PriceIndex, PriceIndexValues> priceIndexValues = ImmutableMap.of();
    private Map<FxIndex, LocalDateDoubleTimeSeries> fxIndexTimeSeries = ImmutableMap.of();

    /**
     * Restricted constructor.
     */
    private Builder() {
      applyDefaults(this);
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 113107279:  // valuationDate
          return valuationDate;
        case -1198118093:  // fxMatrix
          return fxMatrix;
        case -91613053:  // discountFactors
          return discountFactors;
        case 949568445:  // iborIndexRates
          return iborIndexRates;
        case 2138434757:  // overnightIndexRates
          return overnightIndexRates;
        case 1422773131:  // priceIndexValues
          return priceIndexValues;
        case 833356516:  // fxIndexTimeSeries
          return fxIndexTimeSeries;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 113107279:  // valuationDate
          this.valuationDate = (LocalDate) newValue;
          break;
        case -1198118093:  // fxMatrix
          this.fxMatrix = (FxMatrix) newValue;
          break;
        case -91613053:  // discountFactors
          this.discountFactors = (Map<Currency, DiscountFactors>) newValue;
          break;
        case 949568445:  // iborIndexRates
          this.iborIndexRates = (Map<IborIndex, IborIndexRates>) newValue;
          break;
        case 2138434757:  // overnightIndexRates
          this.overnightIndexRates = (Map<OvernightIndex, OvernightIndexRates>) newValue;
          break;
        case 1422773131:  // priceIndexValues
          this.priceIndexValues = (Map<PriceIndex, PriceIndexValues>) newValue;
          break;
        case 833356516:  // fxIndexTimeSeries
          this.fxIndexTimeSeries = (Map<FxIndex, LocalDateDoubleTimeSeries>) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
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

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(256);
      buf.append("ImmutableRatesProvider.Builder{");
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("fxMatrix").append('=').append(JodaBeanUtils.toString(fxMatrix)).append(',').append(' ');
      buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
      buf.append("iborIndexRates").append('=').append(JodaBeanUtils.toString(iborIndexRates)).append(',').append(' ');
      buf.append("overnightIndexRates").append('=').append(JodaBeanUtils.toString(overnightIndexRates)).append(',').append(' ');
      buf.append("priceIndexValues").append('=').append(JodaBeanUtils.toString(priceIndexValues)).append(',').append(' ');
      buf.append("fxIndexTimeSeries").append('=').append(JodaBeanUtils.toString(fxIndexTimeSeries));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
