package com.coremedia.blueprint.caas.labs.wiring;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.CurrentStoreContext;
import com.coremedia.livecontext.ecommerce.common.CommerceBean;
import com.coremedia.livecontext.ecommerce.common.CommerceConnection;
import com.coremedia.livecontext.ecommerce.common.StoreContext;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Optional;

/**
 * todo
 */
@DefaultAnnotation(NonNull.class)
public class SessionProvidingDataFetcher<T> implements DataFetcher<T> {

  private final DataFetcher<T> delegate;

  /**
   * Construct a data fetcher for CommerceBean source objects.
   *
   * @param delegate a data fetcher to do the actual fetching, with a commerce session thread local for the source bean.
   */
  @SuppressWarnings("WeakerAccess")
  public SessionProvidingDataFetcher(DataFetcher<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public T get(DataFetchingEnvironment environment) throws Exception {
    Object source = environment.getSource();
    if (!(source instanceof CommerceBean)) {
      return delegate.get(environment);
    }

    Optional<CommerceConnection> previousConnection = CurrentStoreContext.find()
            .map(StoreContext::getConnection);
    CommerceBean commerceBean = (CommerceBean) source;
    CommerceConnection connection = commerceBean.getContext().getConnection();
    try {
      CurrentStoreContext.set(connection.getStoreContext());
      return delegate.get(environment);
    } finally {
      previousConnection.ifPresentOrElse(conn -> CurrentStoreContext.set(conn.getStoreContext()),
              CurrentStoreContext::remove);
    }
  }
}
