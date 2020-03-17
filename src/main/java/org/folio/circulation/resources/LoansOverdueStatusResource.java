package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.succeeded;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.OverduePeriodCalculatorService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoansOverdueStatusResource extends Resource {
  private final String rootPath;

  public LoansOverdueStatusResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.create(this::getOverdueStatusesForLoans);
  }

  private void getOverdueStatusesForLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository =
      new OverdueFinePolicyRepository(clients);

    final Set<String> loanIds = routingContext
      .getBodyAsJson()
      .getJsonArray("loanIds")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toSet());

    loanRepository.findByIdsWithoutItems(loanIds)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::findLoanPoliciesForLoans))
      .thenComposeAsync(r -> r.after(overdueFinePolicyRepository::findOverdueFinePoliciesForLoans))
      .thenComposeAsync(r -> r.after(records -> getOverdueStatusForLoans(records, clients)))
      .thenApply(r -> r.map(this::mapResultToJson))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<List<OverdueStatus>>> getOverdueStatusForLoans(
    MultipleRecords<Loan> loanRecords, Clients clients) {

    Set<Loan> loans = loanRecords.toKeys(identity());
    final DateTime now = ClockManager.getClockManager().getDateTime();

    return allOf(loans, loan -> getOverdueStatus(loan, now, clients));
  }

  private CompletableFuture<Result<OverdueStatus>> getOverdueStatus(
    Loan loan, DateTime now, Clients clients) {

    final OverdueStatus status = new OverdueStatus(loan, now);

    if (loan.isOpen() && status.isOverdue()) {
      return new OverduePeriodCalculatorService(
        new CalendarRepository(clients), new LoanPolicyRepository(clients))
        .getMinutes(loan, now)
        .thenApply(r -> r.map(status::withOverdueMinutes));
    }

    return completedFuture(succeeded(status));
  }

  private JsonObject mapResultToJson(List<OverdueStatus> statuses) {
    List<JsonObject> jsonStatuses = statuses.stream()
      .map(OverdueStatus::toJson)
      .collect(Collectors.toList());

    return new JsonObject()
      .put("overdueStatus", new JsonArray(jsonStatuses))
      .put("totalRecords", statuses.size());
  }

  private static class OverdueStatus {
    private String loanId;
    private String status;
    private Boolean overdue;
    private Integer overdueMinutes;

    private OverdueStatus(Loan loan, DateTime systemTime) {
      this.loanId = loan.getId();
      this.status = loan.getStatus();

      if (loan.isOpen()) {
        this.overdue = loan.isOverdue(systemTime);
      }
    }

    private OverdueStatus withOverdueMinutes(Integer overdueMinutes) {
      this.overdueMinutes = overdueMinutes;
      return this;
    }

    private boolean isOverdue() {
      return overdue;
    }

    private JsonObject toJson() {
      JsonObject json = new JsonObject();
      write(json, "loanId", loanId);
      write(json, "status", status);
      write(json, "overdue", overdue);
      write(json, "overdueMinutes", overdueMinutes);

      return json;
    }
  }
}
