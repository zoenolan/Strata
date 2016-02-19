package com.opengamma.strata.pricer.bond;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.pricer.impl.bond.DiscountingCapitalIndexedBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.Security;
import com.opengamma.strata.product.bond.CapitalIndexedBond;
import com.opengamma.strata.product.bond.CapitalIndexedBondPaymentPeriod;
import com.opengamma.strata.product.bond.ExpandedCapitalIndexedBond;
import com.opengamma.strata.product.rate.RateObservation;

public class DiscountingCapitalIndexedBondProductPricer {

  /**
   * Default implementation.
   */
  public static final DiscountingCapitalIndexedBondProductPricer DEFAULT =
      new DiscountingCapitalIndexedBondProductPricer(DiscountingCapitalIndexedBondPaymentPeriodPricer.DEFAULT);

  /**
   * Pricer for {@link CapitalIndexedBondPaymentPeriod}.
   */
  private final DiscountingCapitalIndexedBondPaymentPeriodPricer periodPricer;

  public DiscountingCapitalIndexedBondProductPricer(DiscountingCapitalIndexedBondPaymentPeriodPricer periodPricer) {
    this.periodPricer = ArgChecker.notNull(periodPricer, "periodPricer");
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount presentValue(CapitalIndexedBond product, RatesProvider ratesProvider) {
    return presentValue(product, ratesProvider, ratesProvider.getValuationDate());
  }

  // calculate the present value
  CurrencyAmount presentValue(
      CapitalIndexedBond product,
      RatesProvider ratesProvider,
      LocalDate referenceDate) {

    ExpandedCapitalIndexedBond expanded = product.expand();
    double pvNominal =
        periodPricer.presentValue(expanded.getNominalPayment(), ratesProvider);
    double pvCoupon = 0d;
    for (CapitalIndexedBondPaymentPeriod period : expanded.getPeriodicPayments()) {
      if ((product.getExCouponPeriod().getDays() != 0 && period.getDetachmentDate().isAfter(referenceDate)) ||
          (product.getExCouponPeriod().getDays() == 0 && period.getPaymentDate().isAfter(referenceDate))) {
        pvCoupon += periodPricer.presentValue(period, ratesProvider);
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
    CurrencyAmount pv = presentValue(product, ratesProvider, settlementDate);
    StandardId securityId = security.getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = issuerRatesProvider.repoCurveDiscountFactors(
        securityId, legalEntityId, product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotional();
    return pv.getAmount() / df / notional;
  }

  // TODO public double dirtyPriceFromRealYield
  // TODO 

  //-------------------------------------------------------------------------
  public double dirtyRealPriceFromCleanRealPrice(CapitalIndexedBond product, LocalDate settlementDate, double cleanPrice) {
    double notional = product.getNotional();
    return cleanPrice + accruedInterest(product, settlementDate) / notional;
  }

  public double cleanRealPriceFromDirtyRealPrice(CapitalIndexedBond product, LocalDate settlementDate, double dirtyPrice) {
    double notional = product.getNotional();
    return dirtyPrice - accruedInterest(product, settlementDate) / notional;
  }

  public double cleanRealPriceFromCleanNominalPrice(CapitalIndexedBond product, double cleanNominalPrice) {
    final double indexRatio = 0d; // TODO
    return cleanNominalPrice / indexRatio;
  }

  public double cleanNominalPriceFromCleanRealPrice(CapitalIndexedBond product, double cleanRealPrice) {
    final double indexRatio = 0d; // TODO
    return cleanRealPrice * indexRatio;
  }

  public double dirtyRealPriceFromDirtyNominalPrice(CapitalIndexedBond product, double dirtyNominalPrice) {
    final double indexRatio = 0d; // TODO
    return dirtyNominalPrice / indexRatio;
  }

  public double dirtyNominalPriceFromDirtyRealPrice(CapitalIndexedBond product, double dirtyRealPrice) {
    final double indexRatio = 0d; // TODO
    return dirtyRealPrice * indexRatio;
  }

  //-------------------------------------------------------------------------
  // TODO adjust by inflation??
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
          schedulePeriod.yearFraction(product.getDayCount(), scheduleUnadjusted); // TODO check this
    } else {
      result = accruedInterest;
    }
    return result;
  }

  //-------------------------------------------------------------------------
  private int couponIndex(Schedule schedule, LocalDate date) {
    int nbCoupon = schedule.getPeriods().size();
    int couponIndex = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; ++loopcpn) {
      if (schedule.getPeriods().get(loopcpn).getEndDate().isAfter(date)) {
        couponIndex = loopcpn;
        break;
      }
    }
    return couponIndex;
  }

  private double indexRatio(CapitalIndexedBond product,
      RatesProvider ratesProvider, LocalDate settlementDate) {
    LocalDate endReferenceDate = settlementDate.isBefore(ratesProvider.getValuationDate()) ?
        ratesProvider.getValuationDate() : settlementDate;
      SchedulePeriod period = SchedulePeriod.of(product.getPeriodicSchedule().getStartDate(), endReferenceDate);
    RateObservation modifiedObservation = product.getRateCalculation().createRateObservation(period);
    return periodPricer.getRateObservationFn().rate(modifiedObservation, null, null, ratesProvider); // dates not used

  }

}
