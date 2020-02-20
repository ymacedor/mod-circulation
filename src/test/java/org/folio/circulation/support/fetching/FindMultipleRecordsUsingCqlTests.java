package org.folio.circulation.support.fetching;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class FindMultipleRecordsUsingCqlTests {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private GetManyRecordsClient client;

  @Test
  public void shouldFetchRecordsInSinglePage() {
    final FindWithCqlQuery<JsonObject> fetcher = new CqlQueryFinder<>(client,
      "records", identity(), limit(10));

    final Result<CqlQuery> query = exactMatch("Status", "Open");

    when(client.getMany(eq(query.value()), eq(limit(10))))
      .thenReturn(cannedResponse());

    fetcher.findByQuery(query);

    verify(client, times(1)).getMany(eq(query.value()), eq(limit(10)));
  }

  private CompletableFuture<Result<Response>> cannedResponse() {
    return CompletableFuture.completedFuture(Result.of(
      () -> {
        final JsonObject body = new JsonObject();

        body.put("records", new JsonArray());

        return new Response(200, body.encodePrettily(), "application/json");
      }));
  }
}
