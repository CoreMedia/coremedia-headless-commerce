# Manual Tests

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Introducing

## Testing the API

Run the headless server commerce and try the following URL and execute some
queries:

    http://localhost:43180/graphiql

Try the following query and adjust the site parameter if necessary:

```graphql
{
  catalogs(siteId: "coremedia_site_id") {
    __typename
    id
    externalId
    siteId
  }
}
```

A list of all available catalogs should appear. This only works if the demo site
is available and connected to a commerce adapter. The available query API can be
found below the "Docs" link in the upper right corner.
