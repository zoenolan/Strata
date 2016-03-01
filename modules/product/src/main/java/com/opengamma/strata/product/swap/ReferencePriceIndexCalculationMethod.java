/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.swap;

import java.util.Locale;

import org.joda.convert.FromString;
import org.joda.convert.ToString;

import com.opengamma.strata.collect.ArgChecker;

/**
 * Reference price index calculation method. 
 * 
 * This defines how the reference index calculation occurs.
 */
public enum ReferencePriceIndexCalculationMethod {
  
  /**
   * The reference index is the price index of a month.
   * The reference month is linked to the payment date.
   */
  MONTHLY("Monthly"),
  /**
   * The reference index is linearly interpolated between two months.
   * The interpolation is done with the number of days of the payment month.
   * The number of days is counted from the beginning of the month. 
   */
  INTERPOLATED("Interpolated"),
  /**
   * The reference index is linearly interpolated between two months.
   * The interpolation is done with the number of days of the payment month.
   * The number of days is counted from the 10th day of the month. 
   */
  INTERPOLATED_JAPAN("Interpolated-Japan");

  // name
  private final String name;

  // create
  private ReferencePriceIndexCalculationMethod(String name) {
    this.name = name;
  }

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance from the specified unique name.
   * 
   * @param uniqueName  the unique name
   * @return the type
   * @throws IllegalArgumentException if the name is not known
   */
  @FromString
  public static ReferencePriceIndexCalculationMethod of(String uniqueName) {
    ArgChecker.notNull(uniqueName, "uniqueName");
    return valueOf(uniqueName.replace('-', '_').toUpperCase(Locale.ENGLISH));
  }

  //-------------------------------------------------------------------------
  /**
   * Returns the formatted unique name of the type.
   * 
   * @return the formatted string representing the type
   */
  @ToString
  @Override
  public String toString() {
    return name;
  }

}
