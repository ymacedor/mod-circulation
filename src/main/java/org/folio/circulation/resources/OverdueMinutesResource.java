package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.flatMapResult;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.OverduePeriodCalculatorService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

public class OverdueMinutesResource extends Resource {
  private static final String ID_KEY = "id";

  private final String rootPath;
  private OverduePeriodCalculatorService overduePeriodCalculatorService;

  public OverdueMinutesResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::get);
  }

  private void get(RoutingContext routingContext) {
    final UUID loanId = UUID.fromString(
        routingContext.request().getParam(ID_KEY));

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository =
        new OverdueFinePolicyRepository(clients);
    this.overduePeriodCalculatorService =
        new OverduePeriodCalculatorService(new CalendarRepository(clients));

    loanRepository.fetchLoan(loanId.toString())
      .thenApply(this::failIfLoanIsClosed)
      .thenComposeAsync(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenComposeAsync(loanPolicyRepository::findPolicyForLoan)
      .thenComposeAsync(this::getOverdueMinutes)
      .thenApply(flatMapResult(minutes -> mapResultToJson(minutes, loanId)))
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private Result<Loan> failIfLoanIsClosed(Result<Loan> result) {
    return result.succeeded() && result.value().isClosed()
      ? failed(new BadRequestFailure("Loan is closed"))
      : result;
  }

  private CompletableFuture<Result<Integer>> getOverdueMinutes(Result<Loan> result) {
    final DateTime now = ClockManager.getClockManager().getDateTime();

    return result.after(loan -> overduePeriodCalculatorService.getMinutes(loan, now));
  }

  private Result<JsonObject> mapResultToJson(Integer minutes, UUID loanId) {
    return succeeded(
      new JsonObject()
        .put("loanId", loanId.toString())
        .put("overdueMinutes", minutes)
    );
  }

}
