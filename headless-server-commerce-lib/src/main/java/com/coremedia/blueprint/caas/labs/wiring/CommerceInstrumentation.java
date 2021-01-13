package com.coremedia.blueprint.caas.labs.wiring;

import com.coremedia.livecontext.ecommerce.common.CommerceBean;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;

/**
 * This instrumentation initializes the thread local session needed by some commerce API methods
 * if the source is a CommerceBean
 */

@DefaultAnnotation(NonNull.class)
public class CommerceInstrumentation extends SimpleInstrumentation {

  @Override
  public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
    Object source = parameters.getEnvironment().getSource();
    if (source instanceof CommerceBean) {
      return new SessionProvidingDataFetcher<>(dataFetcher);
    }
    return super.instrumentDataFetcher(dataFetcher, parameters);
  }
}
