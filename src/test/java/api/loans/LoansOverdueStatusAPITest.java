package api.loans;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Arrays;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoansOverdueStatusAPITest extends APITests {
  private static final URL OVERDUE_STATUS_URL = InterfaceUrls.loansUrl("/overdue-status");

  private static final String LOAN_ID = "loanId";
  private static final String STATUS = "status";
  private static final String OVERDUE = "overdue";
  private static final String OVERDUE_MINUTES = "overdueMinutes";
  private static final String LOAN_STATUS_OPEN = "Open";
  private static final String LOAN_STATUS_CLOSED = "Closed";

  @Test
  public void shouldReturnOverdueMinutesWhenLoanIsOverdue() {
    DateTime now = DateTime.now(DateTimeZone.UTC);

    use(new LoanPolicyBuilder()
      .withName("1 Hour")
      .rolling(Period.hours(1))
    );

    String loanId = loansFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.james(), now)
      .getId()
      .toString();

    JsonObject request = createRequest(loanId);

    DateTime twoHoursFromNow = now.plusHours(2);

    mockClockManagerToReturnFixedDateTime(twoHoursFromNow);
    JsonObject response = loansClient.create(request, OVERDUE_STATUS_URL, 200).getJson();
    mockClockManagerToReturnDefaultDateTime();

    checkResponse(response,
      new JsonObject()
        .put(LOAN_ID, loanId)
        .put(STATUS, LOAN_STATUS_OPEN)
        .put(OVERDUE, true)
        .put(OVERDUE_MINUTES, 60)
    );
  }

  @Test
  public void shouldNotReturnOverdueMinutesWhenLoanIsNotOverdue() {
    String loanId = loansFixture
      .checkOutByBarcode(itemsFixture.basedUponNod())
      .getId()
      .toString();

    JsonObject request = createRequest(loanId);
    JsonObject response = loansClient.create(request, OVERDUE_STATUS_URL, 200).getJson();

    checkResponse(response,
      new JsonObject()
        .put(LOAN_ID, loanId)
        .put(STATUS, LOAN_STATUS_OPEN)
        .put(OVERDUE, false)
    );
  }

  @Test
  public void shouldReturnCorrectStatusWhenLoanIsClosed() {
    String loanId = loansFixture.createLoan(
      new LoanBuilder()
        .withItem(itemsFixture.basedUponNod())
        .withUserId(usersFixture.james().getId())
        .closed())
      .getId()
      .toString();

    JsonObject request = createRequest(loanId);
    JsonObject response = loansClient.create(request, OVERDUE_STATUS_URL, 200).getJson();

    checkResponse(response,
      new JsonObject()
        .put(LOAN_ID, loanId)
        .put(STATUS, LOAN_STATUS_CLOSED)
    );
  }

  @Test
  public void shouldReturnEmptyArrayWhenLoanDoesNotExist() {
    JsonObject request = createRequest(UUID.randomUUID().toString());
    JsonObject response = loansClient.create(request, OVERDUE_STATUS_URL, 200).getJson();

    checkResponse(response);
  }

  @Test
  public void shouldReturnCorrectStatusesForMixedCase() {
    DateTime now = DateTime.now(DateTimeZone.UTC);

    use(new LoanPolicyBuilder()
      .withName("1 Hour")
      .rolling(Period.hours(1))
    );

    final String oneHourLoanId = loansFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.james(), now)
      .getId()
      .toString();

    use(new LoanPolicyBuilder()
      .withName("3 Hours")
      .rolling(Period.hours(3))
    );

    final String threeHoursLoanId = loansFixture
      .checkOutByBarcode(itemsFixture.basedUponDunkirk(), usersFixture.steve(), now)
      .getId()
      .toString();

    useExampleFixedPolicyCirculationRules();

    final String closedLoanId = loansFixture
      .createLoan(new LoanBuilder()
        .closed()
        .withItem(itemsFixture.basedUponNod())
        .withUserId(usersFixture.steve().getId())
        .withLoanDate(now))
      .getId()
      .toString();

    final String nonExistingLoanId = UUID.randomUUID().toString();

    JsonObject request = createRequest(
      oneHourLoanId,
      threeHoursLoanId,
      closedLoanId,
      nonExistingLoanId
    );

    DateTime twoHoursFromNow = now.plusHours(2);

    mockClockManagerToReturnFixedDateTime(twoHoursFromNow);
    JsonObject response = loansClient.create(request, OVERDUE_STATUS_URL, 200).getJson();
    mockClockManagerToReturnDefaultDateTime();

    checkResponse(response,
      new JsonObject()
        .put(LOAN_ID, oneHourLoanId)
        .put(STATUS, LOAN_STATUS_OPEN)
        .put(OVERDUE, true)
        .put(OVERDUE_MINUTES, 60),
      new JsonObject()
        .put(LOAN_ID, threeHoursLoanId)
        .put(STATUS, LOAN_STATUS_OPEN)
        .put(OVERDUE, false),
      new JsonObject()
        .put(LOAN_ID, closedLoanId)
        .put(STATUS, LOAN_STATUS_CLOSED)
    );
  }

  private JsonObject createRequest(String... loanIds) {
    return new JsonObject().put("loanIds",
      new JsonArray(Arrays.asList(loanIds)));
  }

  private void checkResponse(JsonObject response, JsonObject... statuses) {
    JsonArray actualStatuses = response.getJsonArray("overdueStatus");

    assertEquals(statuses.length, (int) response.getInteger("totalRecords"));
    assertEquals(statuses.length, actualStatuses.size());

    Arrays.stream(statuses).forEach(expectedStatus ->
        assertTrue(actualStatuses.contains(expectedStatus)));
  }

}