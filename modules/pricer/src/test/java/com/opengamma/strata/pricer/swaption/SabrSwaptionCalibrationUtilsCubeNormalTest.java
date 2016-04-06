/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.swaption;

import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.PutCall;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.ImmutableMarketData;
import com.opengamma.strata.basics.market.ReferenceData;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.id.QuoteId;
import com.opengamma.strata.market.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.surface.ConstantNodalSurface;
import com.opengamma.strata.market.surface.NodalSurface;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolator;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.pricer.calibration.CalibrationMeasures;
import com.opengamma.strata.pricer.calibration.CurveCalibrator;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.impl.option.NormalFormulaRepository;
import com.opengamma.strata.pricer.impl.option.RawOptionData;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.swap.SwapTrade;

/**
 * Tests {@link SabrSwaptionCalibrationUtils} for a cube. Realistic dimension and data.
 */
@Test
public class SabrSwaptionCalibrationUtilsCubeNormalTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final LocalDate CALIBRATION_DATE = LocalDate.of(2016, 2, 29);
  private static final ZonedDateTime CALIBRATION_TIME = CALIBRATION_DATE.atTime(10, 0).atZone(ZoneId.of( "Europe/Berlin" ));
  
  private static final SabrSwaptionCalibrationUtils SABR_CALIBRATION = SabrSwaptionCalibrationUtils.DEFAULT;

  private static final String BASE_DIR = "src/test/resources/";
  private static final String GROUPS_FILE = "curve-config/EUR-DSCONOIS-E3BS-E6IRS-group.csv";
  private static final String SETTINGS_FILE = "curve-config/EUR-DSCONOIS-E3BS-E6IRS-settings.csv";
  private static final String NODES_FILE = "curve-config/EUR-DSCONOIS-E3BS-E6IRS-nodes.csv";
  private static final String QUOTES_FILE = "quotes/quotes-20160229-eur.csv";
  private static final Map<Index, LocalDateDoubleTimeSeries> TS = new HashMap<>();
  private static final CurveGroupDefinition CONFIGS =
      RatesCalibrationCsvLoader.load(
          ResourceLocator.of(BASE_DIR + GROUPS_FILE),
          ResourceLocator.of(BASE_DIR + SETTINGS_FILE),
          ResourceLocator.of(BASE_DIR + NODES_FILE)).get(0);
  private static final Map<QuoteId, Double> MAP_MQ = 
      QuotesCsvLoader.load(CALIBRATION_DATE, ImmutableList.of(ResourceLocator.of(BASE_DIR + QUOTES_FILE)));
  private static final ImmutableMarketData MARKET_QUOTES = ImmutableMarketData.builder(CALIBRATION_DATE)
      .addValuesById(MAP_MQ).build();
  
  private static final CalibrationMeasures CALIBRATION_MEASURES = CalibrationMeasures.PAR_SPREAD;
  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.of(1e-9, 1e-9, 100, CALIBRATION_MEASURES);
  private static final ImmutableRatesProvider MULTICURVE =
      CALIBRATOR.calibrate(CONFIGS, CALIBRATION_DATE, MARKET_QUOTES, REF_DATA, TS);  
  
  private static final DiscountingSwapProductPricer SWAP_PRICER = DiscountingSwapProductPricer.DEFAULT;

  private static final DoubleArray MONEYNESS = 
      DoubleArray.ofUnsafe(new double[]{-0.0200, -0.0100, -0.0050, -0.0025, 0.0000, 0.0025, 0.0050, 0.0100, 0.0200});
  private static final List<Period> EXPIRIES = new ArrayList<>();
  private static final List<Tenor> TENORS = new ArrayList<>();
  static {
    EXPIRIES.add(Period.ofMonths(1));
    EXPIRIES.add(Period.ofMonths(3));
    EXPIRIES.add(Period.ofMonths(6));
    EXPIRIES.add(Period.ofYears(1));
    EXPIRIES.add(Period.ofYears(2));
    EXPIRIES.add(Period.ofYears(5));
    TENORS.add(Tenor.TENOR_1Y);
    TENORS.add(Tenor.TENOR_2Y);
    TENORS.add(Tenor.TENOR_5Y);
  }
  private static final Double[][][] DATA_ARRAY_SPARSE = {
    { {null, null, 0.002245, 0.001741, 0.001394, 0.001781, 0.002393, null, null },
    {null, 0.003551, 0.002621, 0.002132, 0.001862, 0.002227, 0.002836, 0.004077, null },
    {0.003918, 0.003098, 0.002411, 0.002104, 0.001982, 0.002185, 0.002563, 0.003409, 0.005046 },
    {0.003859, 0.003247, null, 0.002568, 0.002532, 0.002689, 0.00298, 0.003698, 0.005188 },
    {0.004848, 0.004276, 0.003843, 0.003722, 0.003738, 0.003913, 0.004212, 0.004986, 0.006688 },
    {0.005923, 0.006168, 0.006307, 0.006397, 0.006505, 0.00663, 0.00677, 0.007095, 0.007873 } },
    { {null, null, 0.002744, 0.002253, 0.00201, 0.002384, 0.002995, null, null },
    {null, 0.003925, 0.002964, 0.002492, 0.00228, 0.002634, 0.003233, 0.004496, null },
    {0.00446, 0.003534, 0.002833, 0.002542, 0.002439, 0.002627, 0.002993, 0.003854, 0.005565 },
    {0.004485, null, 0.003279, 0.003112, 0.003086, 0.003233, 0.00351, 0.004227, 0.005766 },
    {0.005405, 0.004738, 0.004308, 0.004196, 0.004217, 0.004389, 0.004682, 0.005457, 0.007197 },
    {0.005993, 0.006223, 0.006366, 0.006459, 0.006568, 0.006694, 0.006835, 0.00716, 0.007933 } },
    { {null, null, 0.003197, 0.002959, 0.002945, 0.00325, 0.003744, null, null },
    {null, null, 0.003745, null, 0.003633, null, 0.004459, null, null },
    {0.004695, 0.004414, 0.004025, 0.003942, 0.004034, 0.004325, 0.00476, 0.005812, 0.008058 },
    {0.00454, 0.004436, 0.004312, 0.004344, 0.004474, 0.004707, 0.00502, 0.005789, 0.007517 },
    {0.005106, 0.005107, 0.005145, 0.005224, 0.005351, 0.005527, 0.005745, 0.006278, 0.007537 },
    {0.00657, 0.006702, 0.006825, 0.006911, 0.007016, 0.00714, 0.007281, null, 0.008408 } }
  };
  private static final List<RawOptionData> DATA_SPARSE = rawData(DATA_ARRAY_SPARSE);
  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolator.of(
      CurveInterpolators.LINEAR.getName(), CurveExtrapolators.FLAT.getName(), CurveExtrapolators.FLAT.getName());
  private static final GridInterpolator2D INTERPOLATOR_2D = new GridInterpolator2D(LINEAR_FLAT, LINEAR_FLAT);

  private static final double TOLERANCE_PRICE_CALIBRATION_LS = 5.0E-4; // Calibration Least Square; result not exact

  @Test
  public void normal_cube() {
    double beta = 0.50;
    NodalSurface betaSurface = ConstantNodalSurface.of("Beta", beta);
    double shift = 0.0300;
    NodalSurface shiftSurface = ConstantNodalSurface.of("Shift", shift);
    SabrParametersSwaptionVolatilities calibrated = SABR_CALIBRATION.calibrateWithFixedBetaAndShift(
        EUR_FIXED_1Y_EURIBOR_6M, CALIBRATION_TIME, ACT_365F, TENORS, DATA_SPARSE,
        MULTICURVE, betaSurface, shiftSurface, INTERPOLATOR_2D);

    for (int looptenor = 0; looptenor < TENORS.size(); looptenor++) {
      double tenor = TENORS.get(looptenor).get(ChronoUnit.YEARS);
      for (int loopexpiry = 0; loopexpiry < EXPIRIES.size(); loopexpiry++) {
        LocalDate expiry = EUR_FIXED_1Y_EURIBOR_6M.getFloatingLeg().getStartDateBusinessDayAdjustment()
            .adjust(CALIBRATION_DATE.plus(EXPIRIES.get(loopexpiry)), REF_DATA);
        LocalDate effectiveDate = EUR_FIXED_1Y_EURIBOR_6M.calculateSpotDateFromTradeDate(expiry, REF_DATA);
        LocalDate endDate = effectiveDate.plus(TENORS.get(looptenor));
        SwapTrade swap = EUR_FIXED_1Y_EURIBOR_6M
            .toTrade(CALIBRATION_DATE, effectiveDate, endDate, BuySell.BUY, 1.0, 0.0);
        double parRate = SWAP_PRICER.parRate(swap.resolve(REF_DATA).getProduct(), MULTICURVE);
        ZonedDateTime expiryDateTime = expiry.atTime(11, 0).atZone(ZoneId.of("Europe/Berlin"));
        double time = calibrated.relativeTime(expiryDateTime);
        for (int loopmoney = 0; loopmoney < MONEYNESS.size(); loopmoney++) {
          if (DATA_ARRAY_SPARSE[looptenor][loopexpiry][loopmoney] != null) {
            double strike = parRate + MONEYNESS.get(loopmoney);
            double volBlack = calibrated.volatility(expiryDateTime, tenor, strike, parRate);
            double priceComputed = BlackFormulaRepository.price(parRate + shift, parRate + MONEYNESS.get(loopmoney) + shift,
                time, volBlack, true);
            double priceNormal = NormalFormulaRepository.price(parRate, parRate + MONEYNESS.get(loopmoney),
                time, DATA_ARRAY_SPARSE[looptenor][loopexpiry][loopmoney], PutCall.CALL);
//            System.out.println(TENORS.get(looptenor).toString() + " / " + EXPIRIES.get(loopexpiry).toString()
//                + ": " + priceComputed + " / " + priceNormal);
            assertEquals(priceComputed, priceNormal, TOLERANCE_PRICE_CALIBRATION_LS);
          }
        }
      }
    }
  }


  private static List<RawOptionData> rawData(Double[][][] dataArray) {
    List<RawOptionData> raw = new ArrayList<>();
    for (int looptenor = 0; looptenor < dataArray.length; looptenor++) {
      raw.add(RawOptionData.of(MONEYNESS, ValueType.SIMPLE_MONEYNESS, EXPIRIES, dataArray[looptenor], ValueType.NORMAL_VOLATILITY));
    }
    return raw;
  }
  
}
