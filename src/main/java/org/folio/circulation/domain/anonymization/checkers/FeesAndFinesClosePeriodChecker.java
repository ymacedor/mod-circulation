package org.folio.circulation.domain.anonymization.checkers;

import java.util.Optional;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;

public class FeesAndFinesClosePeriodChecker extends TimePeriodChecker {

  public FeesAndFinesClosePeriodChecker(Period period) {
    super(period);
  }

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.allFeesAndFinesClosed()
        && findLatestAccountCloseDate(loan).map(this::checkTimePeriodPassed)
          .orElse(false);

  }

  private Optional<DateTime> findLatestAccountCloseDate(Loan loan) {
    return loan.getAccounts()
      .stream()
      .map(Account::getClosedDate)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .max(DateTime::compareTo);
  }

  @Override
  public String getReason() {
    return "intervalAfterFeesAndFinesCloseNotPassed";
  }
}