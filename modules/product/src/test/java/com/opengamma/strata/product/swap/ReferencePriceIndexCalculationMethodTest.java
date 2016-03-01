/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.swap;

import static com.opengamma.strata.collect.TestHelper.assertJodaConvert;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrows;
import static com.opengamma.strata.collect.TestHelper.coverEnum;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test {@link ReferencePriceIndexCalculationMethod}.
 */
@Test
public class ReferencePriceIndexCalculationMethodTest {

  //-------------------------------------------------------------------------
  @DataProvider(name = "name")
  static Object[][] data_name() {
    return new Object[][] {
      {ReferencePriceIndexCalculationMethod.MONTHLY, "Monthly" },
      {ReferencePriceIndexCalculationMethod.INTERPOLATED, "Interpolated" },
      {ReferencePriceIndexCalculationMethod.INTERPOLATED_JAPAN, "Interpolated-Japan" },
    };
  }

  @Test(dataProvider = "name")
  public void test_toString(ReferencePriceIndexCalculationMethod convention, String name) {
    assertEquals(convention.toString(), name);
  }

  @Test(dataProvider = "name")
  public void test_of_lookup(ReferencePriceIndexCalculationMethod convention, String name) {
    assertEquals(ReferencePriceIndexCalculationMethod.of(name), convention);
  }

  public void test_of_lookup_notFound() {
    assertThrows(() -> ReferencePriceIndexCalculationMethod.of("Rubbish"), IllegalArgumentException.class);
  }

  public void test_of_lookup_null() {
    assertThrows(() -> ReferencePriceIndexCalculationMethod.of(null), IllegalArgumentException.class);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    coverEnum(ReferencePriceIndexCalculationMethod.class);
  }

  public void test_serialization() {
    assertSerialization(ReferencePriceIndexCalculationMethod.INTERPOLATED);
  }

  public void test_jodaConvert() {
    assertJodaConvert(ReferencePriceIndexCalculationMethod.class, ReferencePriceIndexCalculationMethod.INTERPOLATED);
  }

}
