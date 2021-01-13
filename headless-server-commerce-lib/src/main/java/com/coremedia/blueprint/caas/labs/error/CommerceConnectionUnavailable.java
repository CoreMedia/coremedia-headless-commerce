package com.coremedia.blueprint.caas.labs.error;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

@DefaultAnnotation(NonNull.class)
public class CommerceConnectionUnavailable implements GraphQLError {

  public static final String ERROR_MSG = "Cannot find commerce connection for siteId.";

  private static final CommerceConnectionUnavailable instance = new CommerceConnectionUnavailable();

  private CommerceConnectionUnavailable() {
  }

  public static CommerceConnectionUnavailable getInstance() {
    return instance;
  }

  @Override
  public String getMessage() {
    return ERROR_MSG;
  }

  @Override
  public List<SourceLocation> getLocations() {
    return new ArrayList<>();
  }

  @Override
  public ErrorType getErrorType() {
    return ErrorType.DataFetchingException;
  }

}
