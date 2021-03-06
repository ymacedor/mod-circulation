package org.folio.circulation.domain.policy;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.json.JsonStringArrayPropertyFetcher;

import io.vertx.core.json.JsonObject;

public class RequestPolicy {
  private final List<String> requestTypes;

  private RequestPolicy(List<String> requestTypes) {
    this.requestTypes = requestTypes;
  }

  public static RequestPolicy from(JsonObject representation) {
    return new RequestPolicy(JsonStringArrayPropertyFetcher
      .toStream(representation, "requestTypes")
      .collect(Collectors.toList()));
  }

  public boolean allowsType(RequestType type) {
    for (String requestType : requestTypes) {
      if (type.nameMatches(requestType))
        return true;
    }
    return false;
  }
}
