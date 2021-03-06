package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.RequestUpdateLogEventMapper.mapToRequestLogEventJson;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_USER_OR_PATRON_GROUP_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_LOANED_TO_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_ALREADY_REQUESTED_BY_SAME_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED_BY_POLICY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.REQUESTING_DISALLOWED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.RequestLoanValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class CreateRequestService {
  private final CreateRequestRepositories repositories;
  private final UpdateUponRequest updateUponRequest;
  private final RequestLoanValidator requestLoanValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final UserManualBlocksValidator userManualBlocksValidator;
  private final EventPublisher eventPublisher;
  private final CirculationErrorHandler errorHandler;

  public CreateRequestService(CreateRequestRepositories repositories,
    UpdateUponRequest updateUponRequest, RequestLoanValidator requestLoanValidator,
    RequestNoticeSender requestNoticeSender, UserManualBlocksValidator userManualBlocksValidator,
    EventPublisher eventPublisher, CirculationErrorHandler errorHandler) {

    this.repositories = repositories;
    this.updateUponRequest = updateUponRequest;
    this.requestLoanValidator = requestLoanValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.userManualBlocksValidator = userManualBlocksValidator;
    this.eventPublisher = eventPublisher;
    this.errorHandler = errorHandler;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> createRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestRepository requestRepository = repositories.getRequestRepository();
    ConfigurationRepository configurationRepository = repositories.getConfigurationRepository();
    AutomatedPatronBlocksValidator automatedPatronBlocksValidator =
      new AutomatedPatronBlocksValidator(repositories.getAutomatedPatronBlocksRepository(),
        messages -> new ValidationErrorFailure(messages.stream()
          .map(message -> new ValidationError(message, new HashMap<>()))
          .collect(Collectors.toList())));

    final Result<RequestAndRelatedRecords> result = succeeded(requestAndRelatedRecords);

    return result.next(RequestServiceUtility::refuseWhenInvalidUserAndPatronGroup)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_USER_OR_PATRON_GROUP_ID, result))
      .next(RequestServiceUtility::refuseWhenUserIsInactive)
      .mapFailure(err -> errorHandler.handleValidationError(err, USER_IS_INACTIVE, result))
      .next(RequestServiceUtility::refuseWhenUserHasAlreadyRequestedItem)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_ALREADY_REQUESTED_BY_SAME_USER, result))
      .after(automatedPatronBlocksValidator::refuseWhenRequestActionIsBlockedForPatron)
      .thenApply(r -> errorHandler.handleValidationResult(r, USER_IS_BLOCKED_AUTOMATICALLY, result))
      .thenCompose(r -> r.after(userManualBlocksValidator::refuseWhenUserIsBlocked))
      .thenApply(r -> errorHandler.handleValidationResult(r, USER_IS_BLOCKED_MANUALLY, result))
      .thenComposeAsync(r -> r.after(when(this::shouldCheckItem, this::checkItem, this::doNothing)))
      .thenComposeAsync(r -> r.after(when(this::shouldCheckPolicy, this::checkPolicy, this::doNothing)))
      .thenComposeAsync(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RequestAndRelatedRecords::withTimeZone))
      .thenApply(r -> r.next(errorHandler::failWithValidationErrors))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateItem::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateLoan::onRequestCreateOrUpdate))
      .thenComposeAsync(r -> r.after(requestRepository::create))
      .thenComposeAsync(r -> r.after(updateUponRequest.updateRequestQueue::onCreate))
      .thenApplyAsync(r -> {
        r.after(t -> eventPublisher.publishLogRecord(mapToRequestLogEventJson(t.getRequest()), REQUEST_CREATED));
        return r.next(requestNoticeSender::sendNoticeOnRequestCreated);
      });
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkItem(
    RequestAndRelatedRecords records) {

    return succeeded(records)
      .next(RequestServiceUtility::refuseWhenItemDoesNotExist)
      .mapFailure(err -> errorHandler.handleValidationError(err, ITEM_DOES_NOT_EXIST, records))
      .next(RequestServiceUtility::refuseWhenRequestTypeIsNotAllowedForItem)
      .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED, records))
      .after(requestLoanValidator::refuseWhenUserHasAlreadyBeenLoanedItem)
      .thenApply(r -> errorHandler.handleValidationResult(r, ITEM_ALREADY_LOANED_TO_SAME_USER, records));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> checkPolicy(
    RequestAndRelatedRecords records) {

    return repositories.getRequestPolicyRepository().lookupRequestPolicy(records)
      .thenApply(r -> r.next(RequestServiceUtility::refuseWhenRequestCannotBeFulfilled)
        .mapFailure(err -> errorHandler.handleValidationError(err, REQUESTING_DISALLOWED_BY_POLICY, r)));
  }

  private CompletableFuture<Result<Boolean>> shouldCheckItem(RequestAndRelatedRecords records) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_ITEM_ID));
  }

  private CompletableFuture<Result<Boolean>> shouldCheckPolicy(RequestAndRelatedRecords records) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_ITEM_ID, ITEM_DOES_NOT_EXIST,
      INVALID_USER_OR_PATRON_GROUP_ID));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> doNothing(
    RequestAndRelatedRecords records) {

    return ofAsync(() -> records);
  }

}
