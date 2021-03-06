package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;

public class FeeFineService {
  private static final Logger log = getLogger(FeeFineService.class);

  private final CollectionResourceClient accountRefundClient;
  private final CollectionResourceClient accountCancelClient;
  private final ResponseInterpreter<AccountActionResponse> accountActionResponseInterpreter;

  public FeeFineService(Clients clients) {
    this.accountCancelClient = clients.accountsCancelClient();
    this.accountRefundClient = clients.accountsRefundClient();

    this.accountActionResponseInterpreter = new ResponseInterpreter<AccountActionResponse>()
      .flatMapOn(201, response -> succeeded(AccountActionResponse.from(response)))
      .otherwise(forwardOnFailure());
  }

  public CompletableFuture<Result<AccountActionResponse>> refundAccount(RefundAccountCommand refundCommand) {
    if (!refundCommand.hasPaidOrTransferredAmount()) {
      log.info("Account has nothing to refund {}", refundCommand.getAccountId());
      return ofAsync(() -> null);
    }

    final RefundAccountRequest refundRequest = RefundAccountRequest.builder()
      .amount(refundCommand.getAccount().getPaidAndTransferredAmount())
      .servicePointId(refundCommand.getCurrentServicePointId())
      .userName(refundCommand.getUserName())
      .refundReason(refundCommand.getRefundReason().getValue())
      .build();

    return accountRefundClient.post(refundRequest.toJson(), refundCommand.getAccountId())
      .thenApply(r -> r.next(accountActionResponseInterpreter::apply));
  }

  public CompletableFuture<Result<AccountActionResponse>> cancelAccount(CancelAccountCommand cancelCommand) {
    final CancelAccountRequest cancelRequest = CancelAccountRequest.builder()
      .servicePointId(cancelCommand.getCurrentServicePointId())
      .userName(cancelCommand.getUserName())
      .cancellationReason(cancelCommand.getCancellationReason().getValue())
      .build();

    return accountCancelClient.post(cancelRequest.toJson(), cancelCommand.getAccountId())
      .thenApply(r -> r.next(accountActionResponseInterpreter::apply));
  }

}
