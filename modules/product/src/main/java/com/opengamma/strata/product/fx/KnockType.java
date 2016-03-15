/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.fx;

/**
 * The knock type of barrier event. 
 * <p>
 * This defines the knock type of {@link Barrier}.
 */
public enum KnockType {

  /** 
   * Knock-in 
   */
  KNOCK_IN,
  /** 
   * Knock-out 
   */
  KNOCK_OUT;
}
