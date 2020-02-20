package org.folio.circulation.support;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CompletableFuture;

public class AsyncResultHelper {
  private AsyncResultHelper() { }

  public static <T> T getFutureResultValue(CompletableFuture<Result<T>> futureResult) {
    try {
      final Result<T> result = futureResult
        .get(1, SECONDS);

      return result.value();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
