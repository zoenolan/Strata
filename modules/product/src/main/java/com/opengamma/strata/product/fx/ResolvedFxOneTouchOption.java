/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.fx;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
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
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.ResolvedProduct;

/**
 * Resolved FX one-touch or no-touch option. 
 * <p>
 * An FX one-touch/no-touch option is a financial instrument that pays one unit of a currency based on the future value of
 * a foreign exchange.
 * <p>
 * For a one touch option, the option holder receives one unit of base/counter currency if the exchange spot rate touches 
 * a given barrier level at any time until expiry, and the payoff is zero otherwise. 
 * For a no touch option, the option holder receives one unit of base/counter currency if the exchange spot rate does not  
 * touch a given barrier level at any time until expiry, and the payoff is zero otherwise. 
 * <p>
 * Note that we assume the payment date of the payoff is specified in the contract and the payment is not made before 
 * the option expires. Thus "one-touch at hit" option is not considered in this class. 
 * <p>
 * For example, a one touch option on an EUR/USD exchange rate with barrier 1.4 and EUR delivery pays one unit of EUR 
 * if the spot touches 1.4 until expiry.
 */
@BeanDefinition(builderScope = "private")
public final class ResolvedFxOneTouchOption
    implements ResolvedProduct, ImmutableBean, Serializable {

  /**
   * Whether the option is long or short.
   */
  @PropertyDefinition(validate = "notNull")
  private final LongShort longShort;
  /**
   * The expiry date-time of the option.
   */
  @PropertyDefinition(validate = "notNull")
  private final ZonedDateTime expiry;
  /**
   * The payoff currency notional. 
   * <p>
   * The payoff currency must be one of the {@code currencyPair}.
   */
  @PropertyDefinition(validate = "notNull")
  private final CurrencyAmount payoffCurrencyNotional;
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
   * The barrier level specified in this field must be based on {@code currencyPair}.
   */
  @PropertyDefinition(validate = "notNull")
  private final Barrier barrier;

  //-------------------------------------------------------------------------
  /**
   * Obtains the resolved option.
   * 
   * @param longShort  long or short
   * @param payoffCurrency  the payoff currency
   * @param notional  the notional
   * @param expiry  the expiry
   * @param currencyPair  the currency pair
   * @param barrier  the barrier
   * @return the instance
   */
  public static ResolvedFxOneTouchOption of(
      LongShort longShort,
      Currency payoffCurrency,
      double notional,
      ZonedDateTime expiry,
      CurrencyPair currencyPair,
      Barrier barrier) {

    CurrencyAmount payoffCurrencyNotional = CurrencyAmount.of(payoffCurrency, notional);
    return new ResolvedFxOneTouchOption(longShort, expiry, payoffCurrencyNotional, currencyPair, barrier);
  }

  /**
   * Obtains the resolved option. 
   * 
   * @param longShort long or short
   * @param payoffCurrencyNotional  the payoff and notional
   * @param expiry  the expiry
   * @param currencyPair  the currency pair
   * @param barrier  the barrier
   * @return the instance
   */
  public static ResolvedFxOneTouchOption of(
      LongShort longShort,
      CurrencyAmount payoffCurrencyNotional,
      ZonedDateTime expiry,
      CurrencyPair currencyPair,
      Barrier barrier) {

    return new ResolvedFxOneTouchOption(longShort, expiry, payoffCurrencyNotional, currencyPair, barrier);
  }

  //-------------------------------------------------------------------------
  @ImmutableValidator
  private void validate() {
    ArgChecker.isTrue(currencyPair.contains(payoffCurrencyNotional.getCurrency()),
        "payoff currency should be one of currency pair");
  }

  //-------------------------------------------------------------------------
  /**
   * Get the payoff currency. 
   * 
   * @return the payoff currency
   */
  public Currency getPayoffCurrency() {
    return payoffCurrencyNotional.getCurrency();
  }

  /**
   * Gets the notional. 
   * 
   * @return the notional
   */
  public double getNotional() {
    return payoffCurrencyNotional.getAmount();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ResolvedFxOneTouchOption}.
   * @return the meta-bean, not null
   */
  public static ResolvedFxOneTouchOption.Meta meta() {
    return ResolvedFxOneTouchOption.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ResolvedFxOneTouchOption.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  private ResolvedFxOneTouchOption(
      LongShort longShort,
      ZonedDateTime expiry,
      CurrencyAmount payoffCurrencyNotional,
      CurrencyPair currencyPair,
      Barrier barrier) {
    JodaBeanUtils.notNull(longShort, "longShort");
    JodaBeanUtils.notNull(expiry, "expiry");
    JodaBeanUtils.notNull(payoffCurrencyNotional, "payoffCurrencyNotional");
    JodaBeanUtils.notNull(currencyPair, "currencyPair");
    JodaBeanUtils.notNull(barrier, "barrier");
    this.longShort = longShort;
    this.expiry = expiry;
    this.payoffCurrencyNotional = payoffCurrencyNotional;
    this.currencyPair = currencyPair;
    this.barrier = barrier;
    validate();
  }

  @Override
  public ResolvedFxOneTouchOption.Meta metaBean() {
    return ResolvedFxOneTouchOption.Meta.INSTANCE;
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
   * Gets the expiry date-time of the option.
   * @return the value of the property, not null
   */
  public ZonedDateTime getExpiry() {
    return expiry;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the payoff currency notional.
   * <p>
   * The payoff currency must be one of the {@code currencyPair}.
   * @return the value of the property, not null
   */
  public CurrencyAmount getPayoffCurrencyNotional() {
    return payoffCurrencyNotional;
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
   * The barrier level specified in this field must be based on {@code currencyPair}.
   * @return the value of the property, not null
   */
  public Barrier getBarrier() {
    return barrier;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ResolvedFxOneTouchOption other = (ResolvedFxOneTouchOption) obj;
      return JodaBeanUtils.equal(longShort, other.longShort) &&
          JodaBeanUtils.equal(expiry, other.expiry) &&
          JodaBeanUtils.equal(payoffCurrencyNotional, other.payoffCurrencyNotional) &&
          JodaBeanUtils.equal(currencyPair, other.currencyPair) &&
          JodaBeanUtils.equal(barrier, other.barrier);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(longShort);
    hash = hash * 31 + JodaBeanUtils.hashCode(expiry);
    hash = hash * 31 + JodaBeanUtils.hashCode(payoffCurrencyNotional);
    hash = hash * 31 + JodaBeanUtils.hashCode(currencyPair);
    hash = hash * 31 + JodaBeanUtils.hashCode(barrier);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(192);
    buf.append("ResolvedFxOneTouchOption{");
    buf.append("longShort").append('=').append(longShort).append(',').append(' ');
    buf.append("expiry").append('=').append(expiry).append(',').append(' ');
    buf.append("payoffCurrencyNotional").append('=').append(payoffCurrencyNotional).append(',').append(' ');
    buf.append("currencyPair").append('=').append(currencyPair).append(',').append(' ');
    buf.append("barrier").append('=').append(JodaBeanUtils.toString(barrier));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ResolvedFxOneTouchOption}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code longShort} property.
     */
    private final MetaProperty<LongShort> longShort = DirectMetaProperty.ofImmutable(
        this, "longShort", ResolvedFxOneTouchOption.class, LongShort.class);
    /**
     * The meta-property for the {@code expiry} property.
     */
    private final MetaProperty<ZonedDateTime> expiry = DirectMetaProperty.ofImmutable(
        this, "expiry", ResolvedFxOneTouchOption.class, ZonedDateTime.class);
    /**
     * The meta-property for the {@code payoffCurrencyNotional} property.
     */
    private final MetaProperty<CurrencyAmount> payoffCurrencyNotional = DirectMetaProperty.ofImmutable(
        this, "payoffCurrencyNotional", ResolvedFxOneTouchOption.class, CurrencyAmount.class);
    /**
     * The meta-property for the {@code currencyPair} property.
     */
    private final MetaProperty<CurrencyPair> currencyPair = DirectMetaProperty.ofImmutable(
        this, "currencyPair", ResolvedFxOneTouchOption.class, CurrencyPair.class);
    /**
     * The meta-property for the {@code barrier} property.
     */
    private final MetaProperty<Barrier> barrier = DirectMetaProperty.ofImmutable(
        this, "barrier", ResolvedFxOneTouchOption.class, Barrier.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "longShort",
        "expiry",
        "payoffCurrencyNotional",
        "currencyPair",
        "barrier");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return longShort;
        case -1289159373:  // expiry
          return expiry;
        case -1767061416:  // payoffCurrencyNotional
          return payoffCurrencyNotional;
        case 1005147787:  // currencyPair
          return currencyPair;
        case -333143113:  // barrier
          return barrier;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ResolvedFxOneTouchOption> builder() {
      return new ResolvedFxOneTouchOption.Builder();
    }

    @Override
    public Class<? extends ResolvedFxOneTouchOption> beanType() {
      return ResolvedFxOneTouchOption.class;
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
    public MetaProperty<LongShort> longShort() {
      return longShort;
    }

    /**
     * The meta-property for the {@code expiry} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZonedDateTime> expiry() {
      return expiry;
    }

    /**
     * The meta-property for the {@code payoffCurrencyNotional} property.
     * @return the meta-property, not null
     */
    public MetaProperty<CurrencyAmount> payoffCurrencyNotional() {
      return payoffCurrencyNotional;
    }

    /**
     * The meta-property for the {@code currencyPair} property.
     * @return the meta-property, not null
     */
    public MetaProperty<CurrencyPair> currencyPair() {
      return currencyPair;
    }

    /**
     * The meta-property for the {@code barrier} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Barrier> barrier() {
      return barrier;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return ((ResolvedFxOneTouchOption) bean).getLongShort();
        case -1289159373:  // expiry
          return ((ResolvedFxOneTouchOption) bean).getExpiry();
        case -1767061416:  // payoffCurrencyNotional
          return ((ResolvedFxOneTouchOption) bean).getPayoffCurrencyNotional();
        case 1005147787:  // currencyPair
          return ((ResolvedFxOneTouchOption) bean).getCurrencyPair();
        case -333143113:  // barrier
          return ((ResolvedFxOneTouchOption) bean).getBarrier();
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
   * The bean-builder for {@code ResolvedFxOneTouchOption}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<ResolvedFxOneTouchOption> {

    private LongShort longShort;
    private ZonedDateTime expiry;
    private CurrencyAmount payoffCurrencyNotional;
    private CurrencyPair currencyPair;
    private Barrier barrier;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 116685664:  // longShort
          return longShort;
        case -1289159373:  // expiry
          return expiry;
        case -1767061416:  // payoffCurrencyNotional
          return payoffCurrencyNotional;
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
        case -1289159373:  // expiry
          this.expiry = (ZonedDateTime) newValue;
          break;
        case -1767061416:  // payoffCurrencyNotional
          this.payoffCurrencyNotional = (CurrencyAmount) newValue;
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
    public ResolvedFxOneTouchOption build() {
      return new ResolvedFxOneTouchOption(
          longShort,
          expiry,
          payoffCurrencyNotional,
          currencyPair,
          barrier);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(192);
      buf.append("ResolvedFxOneTouchOption.Builder{");
      buf.append("longShort").append('=').append(JodaBeanUtils.toString(longShort)).append(',').append(' ');
      buf.append("expiry").append('=').append(JodaBeanUtils.toString(expiry)).append(',').append(' ');
      buf.append("payoffCurrencyNotional").append('=').append(JodaBeanUtils.toString(payoffCurrencyNotional)).append(',').append(' ');
      buf.append("currencyPair").append('=').append(JodaBeanUtils.toString(currencyPair)).append(',').append(' ');
      buf.append("barrier").append('=').append(JodaBeanUtils.toString(barrier));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
