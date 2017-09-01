package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.loanrules.Text2Drools;
import org.folio.circulation.support.ClientUtil;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;

public class LoanRulesResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String rootPath;

  public LoanRulesResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.put(rootPath).handler(BodyHandler.create());

    router.get(rootPath).handler(this::get);
    router.put(rootPath).handler(this::put);
  }

  private void get(RoutingContext routingContext) {
    CollectionResourceClient loansRulesClient = ClientUtil.getLoanRulesClient(routingContext);
    log.debug("get(RoutingContext) client={}", loansRulesClient);

    if (loansRulesClient == null) {
      ServerErrorResponse.internalError(routingContext.response(),
        "Cannot initialise client to storage interface");
      return;
    }

    loansRulesClient.get(response -> {
      try {
        if (response.getStatusCode() != 200) {
          ForwardResponse.forward(routingContext.response(), response);
          return;
        }
        JsonObject loanRules = new JsonObject(response.getBody());
        loanRules.put("loanRulesAsDrools", Text2Drools.convert(loanRules.getString("loanRulesAsTextFile")));
        JsonResponse.success(routingContext.response(), loanRules);
      }
      catch (Throwable e) {
        ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  private void put(RoutingContext routingContext) {
    CollectionResourceClient loansRulesClient = ClientUtil.getLoanRulesClient(routingContext);

    if (loansRulesClient == null) {
      ServerErrorResponse.internalError(routingContext.response(),
        "Cannot initialise client to storage interface");
      return;
    }

    JsonObject rulesInput = routingContext.getBodyAsJson();
    try {
      // try to convert, do not safe if conversion fails
      Text2Drools.convert(rulesInput.getString("loanRulesAsTextFile"));
    } catch (Exception e) {
      ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      return;
    }
    JsonObject rules = rulesInput.copy();
    rules.remove("loanRulesAsDrools");
    loansRulesClient.put(rules, response -> {
      if (response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      } else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }
}