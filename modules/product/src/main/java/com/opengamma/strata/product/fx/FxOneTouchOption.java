/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.fx;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableValidator;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.LongShort;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.market.ReferenceData;
import com.opengamma.strata.basics.market.Resolvable;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.Product;

/**
 * FX one-touch or no-touch option. 
 * <p>
 * An FX one-touch/no-touch option is a financial instrument that pays one unit of a currency based on the future value 
 * of a foreign exchange.
 * <p>
 * For a one touch option, the option holder receives one unit of base/counter currency if the exchange spot rate touches 
 * a given barrier level at any time until expiry, and the payoff is zero otherwise. 
 * For a no touch option, the option holder receives one unit of base/counter currency if the exchange spot rate does not  
 * touch a given barrier level at any time until expiry, and the payoff is zero otherwise. 
 * <p>
 * Note that we assume the payment date of the payoff is specified in the contract and the payment is not made before 
 * the option expires. Thus "one-touch at hit" option is not considered in this class. 
 * <p>
 * For example, a one touch option on an EUR/USD exchange rate with barrier 1.4 and EUR delivery pays one unit of USD 
 * if the spot touches 1.4 until expiry.
 */
@BeanDefinition
public class FxOneTouchOption
    implements Product, Resolvable<ResolvedFxOneTouchOption>, ImmutableBean, Serializable {

  /**
   * Whether the option is long or short.
   */
  @PropertyDefinition(validate = "notNull")
  private final LongShort longShort;
  /**
   * The payoff currency. 
   * <p>
   * The payoff currency must be one of the {@code currencyPair}.
   */
  @PropertyDefinition(validate = "notNull")
  private final Currency payoffCurrency;
  /**
   * The notional of the option.
   * <p>
   * The notional expressed here must be positive.
   * The currency of the notional is specified by {@code payoffCurrency}.
   */
  @PropertyDefinition(validate = "ArgChecker.notNegative")
  private final double notional;
  /**
   * The expiry date of the option.  
   * <p>
   * This date is typically set to be a valid business day.
   * However, the {@code businessDayAdjustment} property may be set to provide a rule for adjustment.
   */
  @PropertyDefinition(validate = "notNull")
  private final AdjustableDate expiryDate;
  /**
   * The expiry time of the option.  
   * <p>
   * The expiry time is related to the expiry date and time-zone.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime expiryTime;
  /**
   * The time-zone of the expiry time.  
   * <p>
   * The expiry time-zone is related to the expiry date and time.
   */
  @PropertyDefinition(validate = "notNull")
  private final ZoneId expiryZone;
  /**
   * The currency pair.
   * <p>
   * The occurrence or non-occurrence of a barrier event is based on the exchange rate 
   * of this currency pair and direction. 
   */
  @PropertyDefinition(validate = "notNull")
  private final CurrencyPair currencyPair;
  /**
   * The barrier. 
   * <p>
   * The barrier level must be represented in the direction of {@code currencyPair}.  
   */
  @PropertyDefinition(validate = "notNull")
  private final Barrier barrier;

  //-------------------------------------------------------------------------
  @ImmutableValidator
  private void validate() {
    ArgChecker.isTrue(currencyPair.contains(payoffCurrency), "payoff currency should be one of currency pair");
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the expiry date-time.
   * <p>
   * The option expires at this date and time.
   * <p>
   * The result is returned by combining the expiry date, time and time-zone.
   * 
   * @return the expiry date and time
   */
  public ZonedDateTime getExpiry() {
    return expiryDate.getUnadjusted().atTime(expiryTime).atZone(expiryZone);
  }

  //-------------------------------------------------------------------------
  @Override
  public ResolvedFxOneTouchOption resolve(ReferenceData refData) {
    return ResolvedFxOneTouchOption.of(
        longShort,
        CurrencyAmount.of(payoffCurrency, notional),
        expiryDate.adjusted(refData).atTime(expiryTime).atZone(expiryZone),
        currencyPair,
        barrier);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code FxOneTouchOption}.
   * @return the meta-bean, not null
   */
  public static FxOneTouchOption.Meta meta() {
    return FxOneTouchOption.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(FxOneTouchOption.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static FxOneTouchOption.Builder builder() {
    return new FxOneTouchOption.Builder();
  }

  /**
   * Restricted constructor.
   * @param builder  the builder to copy from, not null
   */
  protected FxOneTouchOption(FxOneTouchOption.Builder builder) {
    JodaBeanUtils.notNull(builder.longShort, "longShort");
    JodaBeanUtils.notNull(builder.payoffCurrency, "payoffCurrency");
    ArgChecker.notNegative(builder.notional, "notional");
    JodaBeanUtils.notNull(builder.expiryDate, "expiryDate");
    JodaBeanUtils.notNull(builder.expiryTime, "expiryTime");
    JodaBeanUtils.notNull(builder.expiryZone, "expiryZone");
    JodaBeanUtils.notNull(builder.currencyPair, "currencyPair");
    JodaBeanUtils.notNull(builder.barrier, "barrier");
    this.longShort = builder.longShort;
    this.payoffCurrency = builder.payoffCurrency;
    this.notional = builder.notional;
    this.expiryDate = builder.expiryDate;
    this.expiryTime = builder.expiryTime;
    this.expiryZone = builder.expiryZone;
    this.currencyPair = builder.currencyPair;
    this.barrier = builder.barrier;
    validate();
  }

  @Override
  public FxOneTouchOption.Meta metaBean() {
    return FxOneTouchOption.Meta.INSTANCE;
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
   * Gets whether the option is long or short.
   * @return the value of the property, not null
   */
  public LongShort getLongShort() {
    return longShort;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the payoff currency.
   * <p>
   * The payoff currency must be one of the {@code currencyPair}.
   * @return the value of the property, not null
   */
  public Currency getPayoffCurrency() {
    return payoffCurrency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the notional of the option.
   * <p>
   * The notional expressed here must be positive.
   * The currency of the notional is specified by {@code payoffCurrency}.
   * @return the value of the property
   */
  public double getNotional() {
    return notional;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the expiry date of the option.
   * <p>
   * This date is typically set to be a valid business day.
   * However, the {@code businessDayAdjustment} property may be set to provide a rule for adjustment.
   * @return the value of the property, not null
   */
  public AdjustableDate getExpiryDate() {
    return expiryDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the expiry time of the option.
   * <p>
   * The expiry time is related to the expiry date and time-zone.
   * @return the value of the property, not null
   */
  public LocalTime getExpiryTime() {
    return expiryTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-zone of the expiry time.
   * <p>
   * The expiry time-zone is related to the expiry date and time.
   * @return the value of the property, not null
   */
  public ZoneId getExpiryZone() {
    return expiryZone;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the currency pair.
   * <p>
   * The occurrence or non-occurrence of a barrier event is based on the exchange rate
   * of this currency pair and direction.
   * @return the value of the property, not null
   */
  public CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the barrier.
   * <p>
   * The barrier level must be represented in the direction of {@code currencyPair}.
   * @return the value of the property, not null
   */
  public Barrier getBarrier() {
    return barrier;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      FxOneTouchOption other = (FxOneTouchOption) obj;
      return JodaBeanUtils.equal(longShort, other.longShort) &&
          JodaBeanUtils.equal(payoffCurrency, other.payoffCurrency) &&
          JodaBeanUtils.equal(notional, other.notional) &&
          JodaBeanUtils.equal(expiryDate, other.expiryDate) &&
          JodaBeanUtils.equal(expiryTime, other.expiryTime) &&
          JodaBeanUtils.equal(expiryZone, other.expiryZone) &&
          JodaBeanUtils.equal(currencyPair, other.currencyPair) &&
          JodaBeanUtils.equal(barrier, other.barrier);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(longShort);
    hash = hash * 31 + JodaBeanUtils.hashCode(payoffCurrency);
    hash = hash * 31 + JodaBeanUtils.hashCode(notional);
    hash = hash * 31 + JodaBeanUtils.hashCode(expiryDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(expiryTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(expiryZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(currencyPair);
    hash = hash * 31 + JodaBeanUtils.hashCode(barrier);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(288);
    buf.append("FxOneTouchOption{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("longShort").append('=').append(JodaBeanUtils.toString(longShort)).append(',').append(' ');
    buf.append("payoffCurrency").append('=').append(JodaBeanUtils.toString(payoffCurrency)).append(',').append(' ');
    buf.append("notional").append('=').append(JodaBeanUtils.toString(notional)).append(',').append(' ');
    buf.append("expiryDate").append('=').append(JodaBeanUtils.toString(expiryDate)).append(',').append(' ');
    buf.append("expiryTime").append('=').append(JodaBeanUtils.toString(expiryTime)).append(',').append(' ');
    buf.append("expiryZone").append('=').append(JodaBeanUtils.toString(expiryZone)).append(',').append(' ');
    buf.append("currencyPair").append('=').append(JodaBeanUtils.toString(currencyPair)).append(',').append(' ');
    buf.append("barrier").append('=').append(JodaBeanUtils.toString(barrier)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code FxOneTouchOption}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code longShort} property.
     */
    private final MetaProperty<LongShort> longShort = DirectMetaProperty.ofImmutable(
        this, "longShort", FxOneTouchOption.class, LongShort.class);
    /**
     * The meta-property for the {@code payoffCurrency} property.
     */
    private final MetaProperty<Currency> payoffCurrency = DirectMetaProperty.ofImmutable(
        this, "payoffCurrency", FxOneTouchOption.class, Currency.class);
    /**
     * The meta-property for the {@code notional} property.
     */
    private final MetaProperty<Double> notional = DirectMetaProperty.ofImmutable(
        this, "notional", FxOneTouchOption.class, Double.TYPE);
    /**
     * The meta-property for the {@code expiryDate} property.
     */
    private final MetaProperty<AdjustableDate> expiryDate = DirectMetaProperty.ofImmutable(
        this, "expiryDate", FxOneTouchOption.class, AdjustableDate.class);
    /**
     * The meta-property for the {@code expiryTime} property.
     */
    private final MetaProperty<LocalTime> expiryTime = DirectMetaProperty.ofImmutable(
        this, "expiryTime", FxOneTouchOption.class, LocalTime.class);
    /**
     * The meta-property for the {@code expiryZone} property.
     */
    private final MetaProperty<ZoneId> expiryZone = DirectMetaProperty.ofImmutable(
        this, "expiryZone", FxOneTouchOption.class, ZoneId.class);
    /**
     * The meta-property for the {@code currencyPair} property.
     */
    private final MetaProperty<CurrencyPair> currencyPair = DirectMetaProperty.ofImmutable(
        this, "currencyPair", FxOneTouchOption.class, CurrencyPair.class);
    /**
     * The meta-property for the {@code barrier} property.
     */
    private final MetaProperty<Barrier> barrier = DirectMetaProperty.ofImmutable(
        this, "barrier", FxOneTouchOption.class, Barrier.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "longShort",
        "payoffCurrency",
        "notional",
        "expiryDate",
        "expiryTime",
        "expiryZone",
        "currencyPair",
        "barrier");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return longShort;
        case -243533576:  // payoffCurrency
          return payoffCurrency;
        case 1585636160:  // notional
          return notional;
        case -816738431:  // expiryDate
          return expiryDate;
        case -816254304:  // expiryTime
          return expiryTime;
        case -816069761:  // expiryZone
          return expiryZone;
        case 1005147787:  // currencyPair
          return currencyPair;
        case -333143113:  // barrier
          return barrier;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public FxOneTouchOption.Builder builder() {
      return new FxOneTouchOption.Builder();
    }

    @Override
    public Class<? extends FxOneTouchOption> beanType() {
      return FxOneTouchOption.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code longShort} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LongShort> longShort() {
      return longShort;
    }

    /**
     * The meta-property for the {@code payoffCurrency} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Currency> payoffCurrency() {
      return payoffCurrency;
    }

    /**
     * The meta-property for the {@code notional} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> notional() {
      return notional;
    }

    /**
     * The meta-property for the {@code expiryDate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<AdjustableDate> expiryDate() {
      return expiryDate;
    }

    /**
     * The meta-property for the {@code expiryTime} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalTime> expiryTime() {
      return expiryTime;
    }

    /**
     * The meta-property for the {@code expiryZone} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ZoneId> expiryZone() {
      return expiryZone;
    }

    /**
     * The meta-property for the {@code currencyPair} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<CurrencyPair> currencyPair() {
      return currencyPair;
    }

    /**
     * The meta-property for the {@code barrier} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Barrier> barrier() {
      return barrier;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return ((FxOneTouchOption) bean).getLongShort();
        case -243533576:  // payoffCurrency
          return ((FxOneTouchOption) bean).getPayoffCurrency();
        case 1585636160:  // notional
          return ((FxOneTouchOption) bean).getNotional();
        case -816738431:  // expiryDate
          return ((FxOneTouchOption) bean).getExpiryDate();
        case -816254304:  // expiryTime
          return ((FxOneTouchOption) bean).getExpiryTime();
        case -816069761:  // expiryZone
          return ((FxOneTouchOption) bean).getExpiryZone();
        case 1005147787:  // currencyPair
          return ((FxOneTouchOption) bean).getCurrencyPair();
        case -333143113:  // barrier
          return ((FxOneTouchOption) bean).getBarrier();
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
   * The bean-builder for {@code FxOneTouchOption}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<FxOneTouchOption> {

    private LongShort longShort;
    private Currency payoffCurrency;
    private double notional;
    private AdjustableDate expiryDate;
    private LocalTime expiryTime;
    private ZoneId expiryZone;
    private CurrencyPair currencyPair;
    private Barrier barrier;

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(FxOneTouchOption beanToCopy) {
      this.longShort = beanToCopy.getLongShort();
      this.payoffCurrency = beanToCopy.getPayoffCurrency();
      this.notional = beanToCopy.getNotional();
      this.expiryDate = beanToCopy.getExpiryDate();
      this.expiryTime = beanToCopy.getExpiryTime();
      this.expiryZone = beanToCopy.getExpiryZone();
      this.currencyPair = beanToCopy.getCurrencyPair();
      this.barrier = beanToCopy.getBarrier();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return longShort;
        case -243533576:  // payoffCurrency
          return payoffCurrency;
        case 1585636160:  // notional
          return notional;
        case -816738431:  // expiryDate
          return expiryDate;
        case -816254304:  // expiryTime
          return expiryTime;
        case -816069761:  // expiryZone
          return expiryZone;
        case 1005147787:  // currencyPair
          return currencyPair;
        case -333143113:  // barrier
          return barrier;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          this.longShort = (LongShort) newValue;
          break;
        case -243533576:  // payoffCurrency
          this.payoffCurrency = (Currency) newValue;
          break;
        case 1585636160:  // notional
          this.notional = (Double) newValue;
          break;
        case -816738431:  // expiryDate
          this.expiryDate = (AdjustableDate) newValue;
          break;
        case -816254304:  // expiryTime
          this.expiryTime = (LocalTime) newValue;
          break;
        case -816069761:  // expiryZone
          this.expiryZone = (ZoneId) newValue;
          break;
        case 1005147787:  // currencyPair
          this.currencyPair = (CurrencyPair) newValue;
          break;
        case -333143113:  // barrier
          this.barrier = (Barrier) newValue;
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
    public FxOneTouchOption build() {
      return new FxOneTouchOption(this);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets whether the option is long or short.
     * @param longShort  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder longShort(LongShort longShort) {
      JodaBeanUtils.notNull(longShort, "longShort");
      this.longShort = longShort;
      return this;
    }

    /**
     * Sets the payoff currency.
     * <p>
     * The payoff currency must be one of the {@code currencyPair}.
     * @param payoffCurrency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder payoffCurrency(Currency payoffCurrency) {
      JodaBeanUtils.notNull(payoffCurrency, "payoffCurrency");
      this.payoffCurrency = payoffCurrency;
      return this;
    }

    /**
     * Sets the notional of the option.
     * <p>
     * The notional expressed here must be positive.
     * The currency of the notional is specified by {@code payoffCurrency}.
     * @param notional  the new value
     * @return this, for chaining, not null
     */
    public Builder notional(double notional) {
      ArgChecker.notNegative(notional, "notional");
      this.notional = notional;
      return this;
    }

    /**
     * Sets the expiry date of the option.
     * <p>
     * This date is typically set to be a valid business day.
     * However, the {@code businessDayAdjustment} property may be set to provide a rule for adjustment.
     * @param expiryDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expiryDate(AdjustableDate expiryDate) {
      JodaBeanUtils.notNull(expiryDate, "expiryDate");
      this.expiryDate = expiryDate;
      return this;
    }

    /**
     * Sets the expiry time of the option.
     * <p>
     * The expiry time is related to the expiry date and time-zone.
     * @param expiryTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expiryTime(LocalTime expiryTime) {
      JodaBeanUtils.notNull(expiryTime, "expiryTime");
      this.expiryTime = expiryTime;
      return this;
    }

    /**
     * Sets the time-zone of the expiry time.
     * <p>
     * The expiry time-zone is related to the expiry date and time.
     * @param expiryZone  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expiryZone(ZoneId expiryZone) {
      JodaBeanUtils.notNull(expiryZone, "expiryZone");
      this.expiryZone = expiryZone;
      return this;
    }

    /**
     * Sets the currency pair.
     * <p>
     * The occurrence or non-occurrence of a barrier event is based on the exchange rate
     * of this currency pair and direction.
     * @param currencyPair  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currencyPair(CurrencyPair currencyPair) {
      JodaBeanUtils.notNull(currencyPair, "currencyPair");
      this.currencyPair = currencyPair;
      return this;
    }

    /**
     * Sets the barrier.
     * <p>
     * The barrier level must be represented in the direction of {@code currencyPair}.
     * @param barrier  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder barrier(Barrier barrier) {
      JodaBeanUtils.notNull(barrier, "barrier");
      this.barrier = barrier;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(288);
      buf.append("FxOneTouchOption.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("longShort").append('=').append(JodaBeanUtils.toString(longShort)).append(',').append(' ');
      buf.append("payoffCurrency").append('=').append(JodaBeanUtils.toString(payoffCurrency)).append(',').append(' ');
      buf.append("notional").append('=').append(JodaBeanUtils.toString(notional)).append(',').append(' ');
      buf.append("expiryDate").append('=').append(JodaBeanUtils.toString(expiryDate)).append(',').append(' ');
      buf.append("expiryTime").append('=').append(JodaBeanUtils.toString(expiryTime)).append(',').append(' ');
      buf.append("expiryZone").append('=').append(JodaBeanUtils.toString(expiryZone)).append(',').append(' ');
      buf.append("currencyPair").append('=').append(JodaBeanUtils.toString(currencyPair)).append(',').append(' ');
      buf.append("barrier").append('=').append(JodaBeanUtils.toString(barrier)).append(',').append(' ');
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
