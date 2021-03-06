/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.rate;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.index.PriceIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import org.joda.beans.BeanBuilder;

/**
 * Defines the observation of inflation figures from a price index
 * where the start index value is known.
 * <p>
 * A typical application of this rate observation is payments of a capital indexed bond, 
 * where the reference start month is start month of the bond rather than start month of the payment period. 
 * <p>
 * A price index is typically published monthly and has a delay before publication.
 * The rate observed by this instance will be based on the start index value
 * and the observation relative to the end month.
 */
@BeanDefinition(builderScope = "private")
public final class InflationEndMonthRateObservation
    implements RateObservation, ImmutableBean, Serializable {

  /**
   * The start index value. 
   * <p>
   * The published index value of the start month. 
   */
  @PropertyDefinition(validate = "ArgChecker.notNegativeOrZero")
  private final double startIndexValue;
  /**
   * The observation at the end.
   * <p>
   * The inflation rate is the ratio between the start index value and end observation.
   * The end month is typically three months before the end of the period.
   */
  @PropertyDefinition(validate = "notNull")
  private final PriceIndexObservation endObservation;

  //-------------------------------------------------------------------------
  /**
   * Creates an instance from an index, start index value and reference end month.
   * 
   * @param index  the index
   * @param startIndexValue  the start index value
   * @param referenceEndMonth  the reference end month
   * @return the inflation rate observation
   */
  public static InflationEndMonthRateObservation of(
      PriceIndex index,
      double startIndexValue,
      YearMonth referenceEndMonth) {

    return new InflationEndMonthRateObservation(startIndexValue, PriceIndexObservation.of(index, referenceEndMonth));
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Price index.
   * 
   * @return the Price index
   */
  public PriceIndex getIndex() {
    return endObservation.getIndex();
  }

  //-------------------------------------------------------------------------
  @Override
  public void collectIndices(ImmutableSet.Builder<Index> builder) {
    builder.add(getIndex());
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code InflationEndMonthRateObservation}.
   * @return the meta-bean, not null
   */
  public static InflationEndMonthRateObservation.Meta meta() {
    return InflationEndMonthRateObservation.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(InflationEndMonthRateObservation.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  private InflationEndMonthRateObservation(
      double startIndexValue,
      PriceIndexObservation endObservation) {
    ArgChecker.notNegativeOrZero(startIndexValue, "startIndexValue");
    JodaBeanUtils.notNull(endObservation, "endObservation");
    this.startIndexValue = startIndexValue;
    this.endObservation = endObservation;
  }

  @Override
  public InflationEndMonthRateObservation.Meta metaBean() {
    return InflationEndMonthRateObservation.Meta.INSTANCE;
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
   * Gets the start index value.
   * <p>
   * The published index value of the start month.
   * @return the value of the property
   */
  public double getStartIndexValue() {
    return startIndexValue;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the observation at the end.
   * <p>
   * The inflation rate is the ratio between the start index value and end observation.
   * The end month is typically three months before the end of the period.
   * @return the value of the property, not null
   */
  public PriceIndexObservation getEndObservation() {
    return endObservation;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      InflationEndMonthRateObservation other = (InflationEndMonthRateObservation) obj;
      return JodaBeanUtils.equal(startIndexValue, other.startIndexValue) &&
          JodaBeanUtils.equal(endObservation, other.endObservation);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(startIndexValue);
    hash = hash * 31 + JodaBeanUtils.hashCode(endObservation);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(96);
    buf.append("InflationEndMonthRateObservation{");
    buf.append("startIndexValue").append('=').append(startIndexValue).append(',').append(' ');
    buf.append("endObservation").append('=').append(JodaBeanUtils.toString(endObservation));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code InflationEndMonthRateObservation}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code startIndexValue} property.
     */
    private final MetaProperty<Double> startIndexValue = DirectMetaProperty.ofImmutable(
        this, "startIndexValue", InflationEndMonthRateObservation.class, Double.TYPE);
    /**
     * The meta-property for the {@code endObservation} property.
     */
    private final MetaProperty<PriceIndexObservation> endObservation = DirectMetaProperty.ofImmutable(
        this, "endObservation", InflationEndMonthRateObservation.class, PriceIndexObservation.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "startIndexValue",
        "endObservation");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1656407615:  // startIndexValue
          return startIndexValue;
        case 82210897:  // endObservation
          return endObservation;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends InflationEndMonthRateObservation> builder() {
      return new InflationEndMonthRateObservation.Builder();
    }

    @Override
    public Class<? extends InflationEndMonthRateObservation> beanType() {
      return InflationEndMonthRateObservation.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code startIndexValue} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> startIndexValue() {
      return startIndexValue;
    }

    /**
     * The meta-property for the {@code endObservation} property.
     * @return the meta-property, not null
     */
    public MetaProperty<PriceIndexObservation> endObservation() {
      return endObservation;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1656407615:  // startIndexValue
          return ((InflationEndMonthRateObservation) bean).getStartIndexValue();
        case 82210897:  // endObservation
          return ((InflationEndMonthRateObservation) bean).getEndObservation();
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
   * The bean-builder for {@code InflationEndMonthRateObservation}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<InflationEndMonthRateObservation> {

    private double startIndexValue;
    private PriceIndexObservation endObservation;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1656407615:  // startIndexValue
          return startIndexValue;
        case 82210897:  // endObservation
          return endObservation;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1656407615:  // startIndexValue
          this.startIndexValue = (Double) newValue;
          break;
        case 82210897:  // endObservation
          this.endObservation = (PriceIndexObservation) newValue;
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
    public InflationEndMonthRateObservation build() {
      return new InflationEndMonthRateObservation(
          startIndexValue,
          endObservation);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(96);
      buf.append("InflationEndMonthRateObservation.Builder{");
      buf.append("startIndexValue").append('=').append(JodaBeanUtils.toString(startIndexValue)).append(',').append(' ');
      buf.append("endObservation").append('=').append(JodaBeanUtils.toString(endObservation));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
