package org.folio.circulation.resources.agedtolost;

import static org.folio.circulation.support.Result.failed;

import org.folio.circulation.resources.Resource;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ScheduledAgeToLostResource extends Resource {
  public ScheduledAgeToLostResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-age-to-lost", router)
      .create(this::scheduledAgeToLost);
  }

  private void scheduledAgeToLost(RoutingContext routingContext) {
    final WebContext webContext = new WebContext(routingContext);

    webContext.writeResultToHttpResponse(
      failed(new ServerErrorFailure("Not yet implemented")));
  }
}
