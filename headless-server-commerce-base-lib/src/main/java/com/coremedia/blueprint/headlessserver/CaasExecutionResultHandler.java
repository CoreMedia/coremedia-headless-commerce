package com.coremedia.blueprint.headlessserver;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.AbortExecutionException;
import graphql.spring.web.servlet.ExecutionResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static java.lang.invoke.MethodHandles.lookup;

public class CaasExecutionResultHandler implements ExecutionResultHandler {

  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());

  @Override
  public Object handleExecutionResult(CompletableFuture<ExecutionResult> completableFuture) {
    return completableFuture.exceptionally(exc -> {

      if (exc.getCause().getClass().isAssignableFrom(AbortExecutionException.class)) {
        LOG.warn(exc.getMessage());
        return new ExecutionResultImpl((AbortExecutionException) exc.getCause());
      }
      LOG.error(exc.getMessage(), exc);
      return new ExecutionResultImpl(new CaasGraphQLError(exc.getCause()));

    }).thenApply(ExecutionResult::toSpecification);
  }

}
