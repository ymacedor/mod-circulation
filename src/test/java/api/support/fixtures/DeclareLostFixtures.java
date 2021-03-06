package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.declareLoanItemLostURL;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import api.support.RestAssuredClient;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public class DeclareLostFixtures {
  private final RestAssuredClient restAssuredClient;

  public DeclareLostFixtures() {
    this.restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response declareItemLost(DeclareItemLostRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, declareLoanItemLostURL(builder.getLoanId()),
      204, "declare-item-lost-request");
  }

  public Response attemptDeclareItemLost(DeclareItemLostRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, declareLoanItemLostURL(builder.getLoanId()),
      "attempt-declare-item-lost-request");
  }

  public Response declareItemLost(JsonObject loanJson) {
    final UUID loanId = UUID.fromString(loanJson.getString("id"));

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .on(DateTime.now(DateTimeZone.UTC))
      .withComment("testing");

    return declareItemLost(builder);
  }
}
