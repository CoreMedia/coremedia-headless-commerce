package com.coremedia.blueprint.headlessserver;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class CaasGraphQLError implements GraphQLError {

  private Throwable cause;


  public CaasGraphQLError(Throwable cause) {
    this.cause = cause;
  }

  @Override
  public String getMessage() {
    return cause.getMessage();
  }

  @Override
  public List<SourceLocation> getLocations() {
    return new ArrayList<>();
  }

  @Override
  public ErrorType getErrorType() {
    return ErrorType.OperationNotSupported;
  }
}
