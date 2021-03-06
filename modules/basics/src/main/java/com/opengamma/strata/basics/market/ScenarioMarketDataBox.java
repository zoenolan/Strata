/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.market;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

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

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.function.ObjIntFunction;

/**
 * A market data box containing an object which can provide market data for multiple scenarios.
 * 
 * @param <T>  the type of data held in the box
 */
@BeanDefinition
public final class ScenarioMarketDataBox<T> implements ImmutableBean, MarketDataBox<T> {

  /**
   * The market data value which provides data for multiple scenarios.
   */
  @PropertyDefinition(validate = "notNull")
  private final ScenarioMarketDataValue<T> value;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance containing the specified value.
   * 
   * @param <T> the type of the market data value
   * @param value  the market data value which can provide data for multiple scenarios
   * @return a market data box containing the value
   */
  public static <T> ScenarioMarketDataBox<T> of(ScenarioMarketDataValue<T> value) {
    return new ScenarioMarketDataBox<>(value);
  }

  /**
   * Obtains an instance containing the specified market data values, one for each scenario.
   *
   * @param <T> the type of the market data value
   * @param values  the single market data values, one for each scenario
   * @return a scenario market data box containing single market data values, one for each scenario
   */
  @SafeVarargs
  public static <T> ScenarioMarketDataBox<T> of(T... values) {
    return new ScenarioMarketDataBox<>(ScenarioValuesList.of(values));
  }

  /**
   * Obtains an instance containing the specified market data values, one for each scenario.
   *
   * @param <T> the type of the market data value
   * @param values  single market data values, one for each scenario
   * @return a scenario market data box containing single market data values, one for each scenario
   */
  public static <T> ScenarioMarketDataBox<T> of(List<T> values) {
    return new ScenarioMarketDataBox<>(ScenarioValuesList.of(values));
  }

  //-------------------------------------------------------------------------
  @Override
  public T getSingleValue() {
    if (isSingleValue()) {
      return value.getValue(0);
    }
    throw new IllegalStateException("This box does not contain a single value");
  }

  @Override
  public ScenarioMarketDataValue<T> getScenarioValue() {
    return value;
  }

  @Override
  public T getValue(int scenarioIndex) {
    ArgChecker.inRange(scenarioIndex, 0, value.getScenarioCount(), "scenarioIndex");
    return value.getValue(scenarioIndex);
  }

  @Override
  public boolean isSingleValue() {
    return value.getScenarioCount() == 1;
  }

  @Override
  public int getScenarioCount() {
    return value.getScenarioCount();
  }

  @Override
  public Class<?> getMarketDataType() {
    return value.getValue(0).getClass();
  }

  //-------------------------------------------------------------------------
  @Override
  public <R> MarketDataBox<R> apply(Function<T, R> fn) {
    return applyToScenarios(i -> fn.apply(value.getValue(i)));
  }

  @Override
  public <R> MarketDataBox<R> apply(int scenarioCount, ObjIntFunction<T, R> fn) {
    if (scenarioCount != getScenarioCount()) {
      throw new IllegalArgumentException(
          Messages.format(
              "Scenario count {} does not equal the scenario count of the value {}",
              scenarioCount,
              getScenarioCount()));
    }
    List<R> perturbedValues = IntStream.range(0, scenarioCount)
        .mapToObj(idx -> fn.apply(getValue(idx), idx))
        .collect(toImmutableList());
    return MarketDataBox.ofScenarioValues(perturbedValues);
  }

  @Override
  public <U, R> MarketDataBox<R> combineWith(MarketDataBox<U> other, BiFunction<T, U, R> fn) {
    return other.isSingleValue() ?
        combineWithSingle(other, fn) :
        combineWithMultiple(other, fn);
  }

  private <R, U> MarketDataBox<R> combineWithMultiple(MarketDataBox<U> other, BiFunction<T, U, R> fn) {
    ScenarioMarketDataValue<U> otherValue = other.getScenarioValue();

    if (otherValue.getScenarioCount() != value.getScenarioCount()) {
      String message = Messages.format(
          "Scenario values must have the same number of scenarios. {} has {} scenarios, {} has {}",
          value,
          value.getScenarioCount(),
          otherValue,
          otherValue.getScenarioCount());
      throw new IllegalArgumentException(message);
    }
    return applyToScenarios(i -> fn.apply(value.getValue(i), otherValue.getValue(i)));
  }

  private <U, R> MarketDataBox<R> combineWithSingle(MarketDataBox<U> other, BiFunction<T, U, R> fn) {
    U otherValue = other.getSingleValue();
    return applyToScenarios(i -> fn.apply(value.getValue(i), otherValue));
  }

  private <R> MarketDataBox<R> applyToScenarios(Function<Integer, R> fn) {
    List<R> results = IntStream.range(0, value.getScenarioCount())
        .mapToObj(fn::apply)
        .collect(toImmutableList());
    return MarketDataBox.ofScenarioValues(results);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ScenarioMarketDataBox}.
   * @return the meta-bean, not null
   */
  @SuppressWarnings("rawtypes")
  public static ScenarioMarketDataBox.Meta meta() {
    return ScenarioMarketDataBox.Meta.INSTANCE;
  }

  /**
   * The meta-bean for {@code ScenarioMarketDataBox}.
   * @param <R>  the bean's generic type
   * @param cls  the bean's generic type
   * @return the meta-bean, not null
   */
  @SuppressWarnings("unchecked")
  public static <R> ScenarioMarketDataBox.Meta<R> metaScenarioMarketDataBox(Class<R> cls) {
    return ScenarioMarketDataBox.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ScenarioMarketDataBox.Meta.INSTANCE);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @param <T>  the type
   * @return the builder, not null
   */
  public static <T> ScenarioMarketDataBox.Builder<T> builder() {
    return new ScenarioMarketDataBox.Builder<T>();
  }

  private ScenarioMarketDataBox(
      ScenarioMarketDataValue<T> value) {
    JodaBeanUtils.notNull(value, "value");
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ScenarioMarketDataBox.Meta<T> metaBean() {
    return ScenarioMarketDataBox.Meta.INSTANCE;
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
   * Gets the market data value which provides data for multiple scenarios.
   * @return the value of the property, not null
   */
  public ScenarioMarketDataValue<T> getValue() {
    return value;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder<T> toBuilder() {
    return new Builder<T>(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ScenarioMarketDataBox<?> other = (ScenarioMarketDataBox<?>) obj;
      return JodaBeanUtils.equal(value, other.value);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(value);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(64);
    buf.append("ScenarioMarketDataBox{");
    buf.append("value").append('=').append(JodaBeanUtils.toString(value));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ScenarioMarketDataBox}.
   * @param <T>  the type
   */
  public static final class Meta<T> extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    @SuppressWarnings("rawtypes")
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code value} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ScenarioMarketDataValue<T>> value = DirectMetaProperty.ofImmutable(
        this, "value", ScenarioMarketDataBox.class, (Class) ScenarioMarketDataValue.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "value");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 111972721:  // value
          return value;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public ScenarioMarketDataBox.Builder<T> builder() {
      return new ScenarioMarketDataBox.Builder<T>();
    }

    @SuppressWarnings({"unchecked", "rawtypes" })
    @Override
    public Class<? extends ScenarioMarketDataBox<T>> beanType() {
      return (Class) ScenarioMarketDataBox.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code value} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ScenarioMarketDataValue<T>> value() {
      return value;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 111972721:  // value
          return ((ScenarioMarketDataBox<?>) bean).getValue();
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
   * The bean-builder for {@code ScenarioMarketDataBox}.
   * @param <T>  the type
   */
  public static final class Builder<T> extends DirectFieldsBeanBuilder<ScenarioMarketDataBox<T>> {

    private ScenarioMarketDataValue<T> value;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(ScenarioMarketDataBox<T> beanToCopy) {
      this.value = beanToCopy.getValue();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 111972721:  // value
          return value;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder<T> set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 111972721:  // value
          this.value = (ScenarioMarketDataValue<T>) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder<T> set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder<T> setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder<T> setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder<T> setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public ScenarioMarketDataBox<T> build() {
      return new ScenarioMarketDataBox<T>(
          value);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the market data value which provides data for multiple scenarios.
     * @param value  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder<T> value(ScenarioMarketDataValue<T> value) {
      JodaBeanUtils.notNull(value, "value");
      this.value = value;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(64);
      buf.append("ScenarioMarketDataBox.Builder{");
      buf.append("value").append('=').append(JodaBeanUtils.toString(value));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
