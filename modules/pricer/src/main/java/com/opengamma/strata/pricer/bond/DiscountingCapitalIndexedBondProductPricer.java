package com.opengamma.strata.pricer.bond;

import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.value.CompoundedRateType;
import com.opengamma.strata.market.view.IssuerCurveDiscountFactors;
import com.opengamma.strata.math.impl.rootfinding.BracketRoot;
import com.opengamma.strata.math.impl.rootfinding.BrentSingleRootFinder;
import com.opengamma.strata.math.impl.rootfinding.RealSingleRootFinder;
import com.opengamma.strata.pricer.impl.bond.DiscountingCapitalIndexedBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.Security;
import com.opengamma.strata.product.bond.CapitalIndexedBond;
import com.opengamma.strata.product.bond.CapitalIndexedBondPaymentPeriod;
import com.opengamma.strata.product.bond.ExpandedCapitalIndexedBond;
import com.opengamma.strata.product.bond.YieldConvention;
import com.opengamma.strata.product.rate.InflationEndInterpolatedRateObservation;
import com.opengamma.strata.product.rate.InflationEndMonthRateObservation;
import com.opengamma.strata.product.rate.RateObservation;

public class DiscountingCapitalIndexedBondProductPricer {

  /**
   * Default implementation.
   */
  public static final DiscountingCapitalIndexedBondProductPricer DEFAULT =
      new DiscountingCapitalIndexedBondProductPricer(DiscountingCapitalIndexedBondPaymentPeriodPricer.DEFAULT);

  /**
   * The root finder.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  /**
   * Brackets a root.
   */
  private static final BracketRoot ROOT_BRACKETER = new BracketRoot();
  /**
   * Pricer for {@link CapitalIndexedBondPaymentPeriod}.
   */
  private final DiscountingCapitalIndexedBondPaymentPeriodPricer periodPricer;

  public DiscountingCapitalIndexedBondProductPricer(DiscountingCapitalIndexedBondPaymentPeriodPricer periodPricer) {
    this.periodPricer = ArgChecker.notNull(periodPricer, "periodPricer");
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount presentValue(CapitalIndexedBond product, RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider) {
    return presentValue(product, ratesProvider, issuerRatesProvider, ratesProvider.getValuationDate());
  }

  // calculate the present value
  CurrencyAmount presentValue(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      LocalDate referenceDate) {

    validate(ratesProvider, issuerRatesProvider);
    ExpandedCapitalIndexedBond expanded = product.expand();
    IssuerCurveDiscountFactors issuerDiscountFactors = issuerRatesProvider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    double pvNominal =
        periodPricer.presentValue(expanded.getNominalPayment(), ratesProvider, issuerDiscountFactors);
    double pvCoupon = 0d;
    for (CapitalIndexedBondPaymentPeriod period : expanded.getPeriodicPayments()) {
      if ((product.getExCouponPeriod().getDays() != 0 && period.getDetachmentDate().isAfter(referenceDate)) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(referenceDate))) {
        pvCoupon += periodPricer.presentValue(period, ratesProvider, issuerDiscountFactors);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), pvCoupon + pvNominal);
  }

  public CurrencyAmount presentValueWithZSpread(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    return presentValueWithZSpread(product, ratesProvider, issuerRatesProvider, ratesProvider.getValuationDate(), 
        zSpread, compoundedRateType, periodsPerYear);
  }

  // calculate the present value
  CurrencyAmount presentValueWithZSpread(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      LocalDate referenceDate,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    validate(ratesProvider, issuerRatesProvider);
    ExpandedCapitalIndexedBond expanded = product.expand();
    IssuerCurveDiscountFactors issuerDiscountFactors = issuerRatesProvider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    double pvNominal = periodPricer.presentValueWithZSpread(
            expanded.getNominalPayment(), ratesProvider, issuerDiscountFactors, zSpread, compoundedRateType, periodsPerYear);
    double pvCoupon = 0d;
    for (CapitalIndexedBondPaymentPeriod period : expanded.getPeriodicPayments()) {
      if ((product.getExCouponPeriod().getDays() != 0 && period.getDetachmentDate().isAfter(referenceDate)) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(referenceDate))) {
        pvCoupon += periodPricer.presentValueWithZSpread(
            period, ratesProvider, issuerDiscountFactors, zSpread, compoundedRateType, periodsPerYear);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), pvCoupon + pvNominal);
  }

  //-------------------------------------------------------------------------

  public double dirtyNominalPriceFromCurves(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider) {

    CapitalIndexedBond product = security.getProduct();
    LocalDate settlementDate = product.getSettlementDateOffset().adjust(ratesProvider.getValuationDate());
    return dirtyNominalPriceFromCurves(security, ratesProvider, issuerRatesProvider, settlementDate);
  }

  double dirtyNominalPriceFromCurves(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      LocalDate settlementDate) {

    CapitalIndexedBond product = security.getProduct();
    CurrencyAmount pv = presentValue(product, ratesProvider, issuerRatesProvider, settlementDate);
    StandardId securityId = security.getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = issuerRatesProvider.repoCurveDiscountFactors(
        securityId, legalEntityId, product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotional();
    return pv.getAmount() / df / notional;
  }

  public double dirtyNominalPriceFromCurvesWithZSpread(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CapitalIndexedBond product = security.getProduct();
    LocalDate settlementDate = product.getSettlementDateOffset().adjust(ratesProvider.getValuationDate());
    return dirtyNominalPriceFromCurvesWithZSpread(
        security, ratesProvider, issuerRatesProvider, settlementDate, zSpread, compoundedRateType, periodsPerYear);
  }

  double dirtyNominalPriceFromCurvesWithZSpread(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      LocalDate settlementDate,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CapitalIndexedBond product = security.getProduct();
    CurrencyAmount pv = presentValueWithZSpread(
        product, ratesProvider, issuerRatesProvider, settlementDate, zSpread, compoundedRateType, periodsPerYear);
    StandardId securityId = security.getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = issuerRatesProvider.repoCurveDiscountFactors(
        securityId, legalEntityId, product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotional();
    return pv.getAmount() / df / notional;
  }

  //-------------------------------------------------------------------------

  public double dirtyPriceFromRealYield(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    Schedule scheduleAdjusted = product.getPeriodicSchedule().createSchedule();
    Schedule scheduleUnadjusted = scheduleAdjusted.toUnadjusted();
    List<Double> coupon = product.getRateCalculation().getGearing().orElse(ValueSchedule.ALWAYS_1)
        .resolveValues(scheduleAdjusted.getPeriods());
    int nbCoupon = scheduleAdjusted.getPeriods().size();
    YieldConvention yieldConvention = product.getYieldConvention();
    if (yieldConvention.equals(YieldConvention.US_IL_REAL)) {
      double pvAtFirstCoupon;
      double cpnRate = coupon.get(0);
      double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
      if (Math.abs(yield) > 1.0E-8) {
        double factorOnPeriod = 1 + yield / couponPerYear;
        double vn = Math.pow(factorOnPeriod, 1 - nbCoupon);
        pvAtFirstCoupon = cpnRate * couponPerYear / yield * (factorOnPeriod - vn) + vn;
      } else {
        pvAtFirstCoupon = cpnRate * nbCoupon + 1d;
      }
      return pvAtFirstCoupon /
          (1d + factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate) * yield / couponPerYear);
    }

    int couponIndex = couponIndex(scheduleUnadjusted, settlementDate);
    double realRate = coupon.get(couponIndex);
    double firstYearFraction =
        scheduleUnadjusted.getPeriod(couponIndex).yearFraction(product.getDayCount(), scheduleUnadjusted);
    double v = 1d / (1d + yield / product.getPeriodicSchedule().getFrequency().eventsPerYear());
    if (yieldConvention.equals(YieldConvention.INDEX_LINKED_FLOAT)) {
      ExpandedCapitalIndexedBond expanded = product.expand();
      RateObservation obs2 = expanded.getPeriodicPayments().get(couponIndex + 1).getRateObservation();
      LocalDateDoubleTimeSeries ts = ratesProvider.priceIndexValues(product.getRateCalculation().getIndex())
          .getFixings();
      YearMonth lastKnownFixingMonth = YearMonth.from(ts.getLatestDate());
      YearMonth paymentMonth1 = YearMonth.from(expanded.getPeriodicPayments().get(couponIndex).getPaymentDate());
      double indexRatio = ts.getLatestValue() / product.getStartIndexValue();
      YearMonth paymentMonth2 = YearMonth.from(expanded.getPeriodicPayments().get(couponIndex + 1).getPaymentDate());
      YearMonth endFixingMonth2 = null;
      if (obs2 instanceof InflationEndInterpolatedRateObservation) {
        endFixingMonth2 = ((InflationEndInterpolatedRateObservation) obs2).getReferenceEndInterpolationMonth();
      } else if (obs2 instanceof InflationEndMonthRateObservation) {
        endFixingMonth2 = ((InflationEndMonthRateObservation) obs2).getReferenceEndMonth();
      } else {
        throw new IllegalArgumentException("The rate observation " + obs2.toString() + " is not supported.");
      }
      double lag1 = ChronoUnit.MONTHS.between(paymentMonth1, lastKnownFixingMonth);
      double lag2 = ChronoUnit.MONTHS.between(endFixingMonth2, paymentMonth2);
      double nbMonth = Math.max(lag1 + lag2, 0d);
      double u = Math.sqrt(1d / (1d + 0.03));
      double a = indexRatio * Math.pow(u, nbMonth / 6d);
      double firstCashFlow = firstYearFraction * realRate * indexRatio;
      if (couponIndex == nbCoupon - 1) {
        return Math.pow(u * v, factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate)) *
            (firstCashFlow + 1d) * a / u;
      } else {
        double secondYearFraction =
            scheduleUnadjusted.getPeriod(couponIndex  + 1).yearFraction(product.getDayCount(), scheduleUnadjusted);
        double secondCashFlow = secondYearFraction * realRate * indexRatio;
        double vn = Math.pow(v, nbCoupon - 1);
        double pvAtFirstCoupon =
            firstCashFlow + secondCashFlow * u * v + a * realRate * v * v * (1d - vn / v) / (1d - v) + a * vn;
        return pvAtFirstCoupon *
            Math.pow(u * v, ratioPeriodToNextCoupon(expanded.getPeriodicPayments().get(couponIndex), settlementDate));
      }
    }
    if (yieldConvention.equals(YieldConvention.UK_IL_BOND)) {
      double firstCashFlow = firstYearFraction * realRate;
      if (couponIndex == nbCoupon - 1) {
        return Math.pow(v, factorToNextCoupon(scheduleUnadjusted, product.getDayCount(),
            settlementDate)) * (firstCashFlow + 1);
      } else {
        double secondYearFraction =
            scheduleUnadjusted.getPeriod(couponIndex + 1).yearFraction(product.getDayCount(), scheduleUnadjusted);
        double secondCashFlow = secondYearFraction * realRate;
        double vn = Math.pow(v, nbCoupon - 1);
        double pvAtFirstCoupon =
            firstCashFlow + secondCashFlow * v + realRate * v * v * (1d - vn / v) / (1d - v) + vn;
        return pvAtFirstCoupon *
            Math.pow(v, factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate));
      }
    }
    throw new IllegalArgumentException(
        "The convention " + product.getYieldConvention().toString() + " is not supported.");
  }

  public double realYieldFromDirtyPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double dirtyPrice) {

    final Function<Double, Double> priceResidual = new Function<Double, Double>() {
      @Override
      public Double apply(final Double y) {
        return dirtyPriceFromRealYield(product, ratesProvider, settlementDate, y) - dirtyPrice;
      }
    };
    double[] range = ROOT_BRACKETER.getBracketedPoints(priceResidual, -0.05, 0.10);
    double yield = ROOT_FINDER.getRoot(priceResidual, range[0], range[1]);
    return yield;
  }

  public double realYieldFromCurves(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider) {

    CapitalIndexedBond product = security.getProduct();
    LocalDate settlementDate = product.getSettlementDateOffset().adjust(ratesProvider.getValuationDate());
    double dirtyPrice;
    if (product.getYieldConvention().equals(YieldConvention.INDEX_LINKED_FLOAT)) {
      dirtyPrice = dirtyNominalPriceFromCurves(security, ratesProvider, issuerRatesProvider);
    } else {
      double dirtyNominalPrice = dirtyNominalPriceFromCurves(security, ratesProvider, issuerRatesProvider);
      dirtyPrice = dirtyRealPriceFromDirtyNominalPrice(product, ratesProvider, settlementDate, dirtyNominalPrice);
    }
    return realYieldFromDirtyPrice(product, ratesProvider, settlementDate, dirtyPrice);
  }

  public double cleanPriceFromRealYield(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    double dirtyPrice = dirtyPriceFromRealYield(product, ratesProvider, settlementDate, yield);
    if (product.getYieldConvention().equals(YieldConvention.INDEX_LINKED_FLOAT)) {
      return cleanNominalPriceFromDirtyNominalPrice(product, ratesProvider, settlementDate, dirtyPrice);
    }
    return cleanRealPriceFromDirtyRealPrice(product, settlementDate, dirtyPrice);
  }

  public double dirtyPriceFromStandardYield(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    ExpandedCapitalIndexedBond expanded = product.expand();
    Schedule scheduleUnadjusted = product.getPeriodicSchedule().createSchedule().toUnadjusted();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double notional = product.getNotional();
    double factorOnPeriod = 1d + yield / couponPerYear;
    double pvAtFirstCoupon = 0d;
    int pow = 0;
    double factorToNext = factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate);
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      CapitalIndexedBondPaymentPeriod period = expanded.getPeriodicPayments().get(loopcpn);
      if ((product.getExCouponPeriod().getDays() != 0 && !settlementDate.isAfter(period.getDetachmentDate())) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(settlementDate))) {
        pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow);
        ++pow;
      }
    }
    pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow - 1);
    return pvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNext);
  }

  //-------------------------------------------------------------------------

  /**
   * Calculates the modified duration from a standard yield.
   * @param bond The bond
   * @param yield The yield
   * @return The modified duration
   */
  public double modifiedDurationFromRealYieldFiniteDifference(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    double price = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield);
    double priceplus = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield + 10e-6);
    double priceminus = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield - 10e-6);
    return -(priceplus - priceminus) / (2 * price * 10e-6);
  }

  /**
   * Calculates the modified duration from a standard yield.
   * @param bond The bond
   * @param yield The yield
   * @return The modified duration
   */
  public double convexityFromRealYieldFiniteDifference(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    double price = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield);
    double priceplus = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield + 10e-6);
    double priceminus = cleanPriceFromRealYield(product, ratesProvider, settlementDate, yield - 10e-6);
    return (priceplus - 2 * price + priceminus) / (price * 10e-6 * 10e-6);
  }

  public double modifiedDurationFromStandardYield(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    ExpandedCapitalIndexedBond expanded = product.expand();
    Schedule scheduleUnadjusted = product.getPeriodicSchedule().createSchedule().toUnadjusted();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double notional = product.getNotional();
    double factorOnPeriod = 1d + yield / couponPerYear;
    double mdAtFirstCoupon = 0d;
    double pvAtFirstCoupon = 0d;
    int pow = 0;
    double factorToNext = factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate);
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      CapitalIndexedBondPaymentPeriod period = expanded.getPeriodicPayments().get(loopcpn);
      if ((product.getExCouponPeriod().getDays() != 0 && !settlementDate.isAfter(period.getDetachmentDate())) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(settlementDate))) {
        mdAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow + 1) *
            (loopcpn + factorToNext) / couponPerYear;
        pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow);
        ++pow;
      }
    }
    mdAtFirstCoupon += notional * (pow - 1d + factorToNext) / (couponPerYear * Math.pow(factorOnPeriod, pow));
    pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow - 1);
    double pv = pvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNext);
    double md = mdAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNext) / pv;
    return md;
  }

  public double convexityFromYieldStandard(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double yield) {

    ExpandedCapitalIndexedBond expanded = product.expand();
    Schedule scheduleUnadjusted = product.getPeriodicSchedule().createSchedule().toUnadjusted();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double notional = product.getNotional();
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double factorOnPeriod = 1d + yield / couponPerYear;
    double cvAtFirstCoupon = 0;
    double pvAtFirstCoupon = 0;
    int pow = 0;
    double factorToNext = factorToNextCoupon(scheduleUnadjusted, product.getDayCount(), settlementDate);
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      CapitalIndexedBondPaymentPeriod period = expanded.getPeriodicPayments().get(loopcpn);
      if ((product.getExCouponPeriod().getDays() != 0 && !settlementDate.isAfter(period.getDetachmentDate())) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(settlementDate))) {
        cvAtFirstCoupon += notional * (pow + factorToNext) * (pow + factorToNext + 1d) /
            (Math.pow(factorOnPeriod, pow + 2) * couponPerYear * couponPerYear);
        pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow);
        ++pow;
      }
    }
    cvAtFirstCoupon += notional * (pow - 1d + factorToNext) *
        (pow + factorToNext) / (Math.pow(factorOnPeriod, pow + 1) * couponPerYear * couponPerYear);
    pvAtFirstCoupon += notional / Math.pow(factorOnPeriod, pow - 1);
    double pv = pvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNext);
    double cv = cvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNext) / pv;
    return cv;
  }

  //-------------------------------------------------------------------------
  public double dirtyRealPriceFromCleanRealPrice(CapitalIndexedBond product, LocalDate settlementDate, double cleanPrice) {
    double notional = product.getNotional();
    return cleanPrice + accruedInterest(product, settlementDate) / notional;
  }

  public double cleanRealPriceFromDirtyRealPrice(CapitalIndexedBond product, LocalDate settlementDate, double dirtyPrice) {
    double notional = product.getNotional();
    return dirtyPrice - accruedInterest(product, settlementDate) / notional;
  }

  public double dirtyNominalPriceFromCleanNominalPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double cleanPrice) {

    double notional = product.getNotional();
    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return cleanPrice + accruedInterest(product, settlementDate) / notional * indexRatio;
  }

  public double cleanNominalPriceFromDirtyNominalPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double dirtyPrice) {

    double notional = product.getNotional();
    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return dirtyPrice - accruedInterest(product, settlementDate) / notional * indexRatio;
  }

  public double cleanRealPriceFromCleanNominalPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double cleanNominalPrice) {

    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return cleanNominalPrice / indexRatio;
  }

  public double cleanNominalPriceFromCleanRealPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double cleanRealPrice) {

    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return cleanRealPrice * indexRatio;
  }

  public double dirtyRealPriceFromDirtyNominalPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double dirtyNominalPrice) {

    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return dirtyNominalPrice / indexRatio;
  }

  public double dirtyNominalPriceFromDirtyRealPrice(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double dirtyRealPrice) {

    double indexRatio = indexRatio(product, ratesProvider, settlementDate);
    return dirtyRealPrice * indexRatio;
  }

  //-------------------------------------------------------------------------

  public double zSpreadFromCurvesAndCleanPrice(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      double cleanPrice,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CapitalIndexedBond product = security.getProduct();
    LocalDate settlementDate = product.getSettlementDateOffset().adjust(ratesProvider.getValuationDate());
    final Function<Double, Double> residual = new Function<Double, Double>() {
      @Override
      public Double apply(Double z) {
        double dirtyPrice = dirtyNominalPriceFromCurvesWithZSpread(
            security, ratesProvider, issuerRatesProvider, settlementDate, z, compoundedRateType, periodsPerYear);
        if (product.getYieldConvention().equals(YieldConvention.INDEX_LINKED_FLOAT)) {
          return cleanNominalPriceFromDirtyNominalPrice(product, ratesProvider, settlementDate, dirtyPrice) - cleanPrice;
        }
        return cleanRealPriceFromDirtyRealPrice(product, settlementDate, dirtyPrice) - cleanPrice;
      }
    };
    double[] range = ROOT_BRACKETER.getBracketedPoints(residual, -0.5, 0.5); // Starting range is [-1%, 1%]
    return ROOT_FINDER.getRoot(residual, range[0], range[1]);
  }

  public double zSpreadFromCurvesAndPV(
      Security<CapitalIndexedBond> security,
      RatesProvider ratesProvider,
      LegalEntityDiscountingProvider issuerRatesProvider,
      CurrencyAmount presentValue,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    CapitalIndexedBond product = security.getProduct();
    LocalDate settlementDate = product.getSettlementDateOffset().adjust(ratesProvider.getValuationDate());
    final Function<Double, Double> residual = new Function<Double, Double>() {
      @Override
      public Double apply(Double z) {
        return presentValueWithZSpread(product, ratesProvider, issuerRatesProvider, settlementDate, periodsPerYear,
            compoundedRateType, periodsPerYear).getAmount() - presentValue.getAmount();
      }
    };
    double[] range = ROOT_BRACKETER.getBracketedPoints(residual, -0.5, 0.5); // Starting range is [-1%, 1%]
    return ROOT_FINDER.getRoot(residual, range[0], range[1]);
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount netAmount(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate settlementDate,
      double cleanPriceReal) {

    double notional = product.getNotional();
    double netAmountRealByUnit = cleanPriceReal + accruedInterest(product, settlementDate) / notional;
    double netAmount = settlementDate.isBefore(ratesProvider.getValuationDate()) ? 0d :
        indexRatio(product, ratesProvider, settlementDate) * notional;
    return CurrencyAmount.of(product.getCurrency(), netAmount * netAmountRealByUnit);
  }

  //-------------------------------------------------------------------------
  public double accruedInterest(CapitalIndexedBond product, LocalDate settlementDate) {
    Schedule scheduleAdjusted = product.getPeriodicSchedule().createSchedule();
    Schedule scheduleUnadjusted = scheduleAdjusted.toUnadjusted();
    if (scheduleUnadjusted.getPeriods().get(0).getStartDate().isAfter(settlementDate)) {
      return 0d;
    }
    double notional = product.getNotional();
    int couponIndex = couponIndex(scheduleUnadjusted, settlementDate);
    SchedulePeriod schedulePeriod = scheduleUnadjusted.getPeriod(couponIndex);
    LocalDate previousAccrualDate = schedulePeriod.getStartDate();
    LocalDate paymentDate = scheduleAdjusted.getPeriod(couponIndex).getEndDate();

    double fixedRate = product.getRateCalculation().getGearing().orElse(ValueSchedule.ALWAYS_1)
        .resolveValues(scheduleAdjusted.getPeriods()).get(couponIndex);
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double accruedInterest = product.getDayCount()
        .yearFraction(previousAccrualDate, settlementDate, scheduleUnadjusted) * fixedRate * couponPerYear * notional;
    DaysAdjustment exCouponDays = product.getExCouponPeriod();
    double result = 0d;
    if (exCouponDays.getDays() != 0 && settlementDate.isAfter(exCouponDays.adjust(paymentDate))) {
      result = accruedInterest - notional * fixedRate * couponPerYear *
          schedulePeriod.yearFraction(product.getDayCount(), scheduleUnadjusted);
    } else {
      result = accruedInterest;
    }
    return result;
  }

  //-------------------------------------------------------------------------
  private double ratioPeriodToNextCoupon(CapitalIndexedBondPaymentPeriod period, LocalDate settlementDate) {
    double nbDayToSpot = ChronoUnit.DAYS.between(settlementDate, period.getUnadjustedEndDate());
    double nbDaysPeriod = ChronoUnit.DAYS.between(period.getUnadjustedEndDate(), period.getUnadjustedStartDate());
    return nbDayToSpot / nbDaysPeriod;
  }

  private double factorToNextCoupon(Schedule scheduleUnadjusted, DayCount daycount, LocalDate settlementDate) {
    if (scheduleUnadjusted.getPeriod(0).getUnadjustedStartDate().isAfter(settlementDate)) {
      return 0d;
    }
    int couponIndex = couponIndex(scheduleUnadjusted, settlementDate);
    SchedulePeriod schedulePeriod = scheduleUnadjusted.getPeriod(couponIndex);
    LocalDate previousAccrualDate = schedulePeriod.getStartDate();
    double factorSpot = daycount.yearFraction(previousAccrualDate, settlementDate, scheduleUnadjusted);
    double factorPeriod = scheduleUnadjusted.getPeriod(couponIndex).yearFraction(daycount, scheduleUnadjusted);
    return (factorPeriod - factorSpot) / factorPeriod;
  }

  private int couponIndex(Schedule schedule, LocalDate date) {
    int nbCoupon = schedule.getPeriods().size();
    int couponIndex = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; ++loopcpn) {
      if (schedule.getPeriods().get(loopcpn).getUnadjustedEndDate().isAfter(date)) {
        couponIndex = loopcpn;
        break;
      }
    }
    return couponIndex;
  }

  private double indexRatio(CapitalIndexedBond product, RatesProvider ratesProvider, LocalDate settlementDate) {
    LocalDate endReferenceDate = settlementDate.isBefore(ratesProvider.getValuationDate()) ?
        ratesProvider.getValuationDate() : settlementDate;
    RateObservation modifiedObservation =
        product.getRateCalculation().createRateObservation(endReferenceDate, product.getStartIndexValue());
    return 1d + periodPricer.getRateObservationFn().rate(
        modifiedObservation,
        product.getPeriodicSchedule().getStartDate(),
        product.getPeriodicSchedule().getEndDate(),
        ratesProvider); // dates not used
  }

  private void validate(RatesProvider ratesProvider, LegalEntityDiscountingProvider issuerRatesProvider) {
    assertTrue(ratesProvider.getValuationDate().isEqual(issuerRatesProvider.getValuationDate()),
        "the rates providers should be for the same date");
  }

}
