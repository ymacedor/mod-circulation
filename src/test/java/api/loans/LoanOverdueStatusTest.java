package api.loans;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class LoanOverdueStatusTest extends APITests {
  @Test
  public void shouldReturnOverdueMinutesWhenLoanIsOverdue() {
    DateTime now = DateTime.now(DateTimeZone.UTC);
    DateTime dueDatePlusTenMinutes = now.plusWeeks(3).plusMinutes(10);

    IndividualResource loan = loansFixture.checkOutByBarcode(
      itemsFixture.basedUponNod(),
      usersFixture.james(),
      now);

    mockClockManagerToReturnFixedDateTime(dueDatePlusTenMinutes);
    IndividualResource actualResponse = loansClient.get(getUrlForLoan(loan));
    mockClockManagerToReturnDefaultDateTime();

    JsonObject expectedResponse = new JsonObject()
      .put("overdue", true)
      .put("overdueMinutes", 10);

    assertEquals(expectedResponse, actualResponse.getJson());
  }

  @Test
  public void shouldNotReturnOverdueMinutesWhenLoanIsNotOverdue() {
    DateTime now = DateTime.now(DateTimeZone.UTC);

    IndividualResource loan = loansFixture.checkOutByBarcode(
      itemsFixture.basedUponNod(),
      usersFixture.james(),
      now);

    IndividualResource actualResponse = loansClient.get(getUrlForLoan(loan));

    JsonObject expectedResponse = new JsonObject().put("overdue", false);

    assertEquals(expectedResponse, actualResponse.getJson());
  }

  @Test
  public void shouldReturnErrorWhenLoanIsClosed() {
    IndividualResource loan = loansFixture.createLoan(new LoanBuilder().closed()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(usersFixture.steve().getId()));

    IndividualResource actualResponse = loansClient.get(getUrlForLoan(loan));
  }



  private URL getUrlForLoan(IndividualResource loanResource) {
    return InterfaceUrls.loansUrl(
      String.format("/%s/overdue-status", loanResource.getId()));
  }

}