# Manual Tests

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Introducing

## Testing the API

### Using the test site and content

Import the test data into your CoreMedia content repository. The test data contains a new site for the sample React client which is already enabled for delivery
with the headless server.   
Run the headless server and try the following URLs:

    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/navigation
    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/teasables/home.hero
    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/teasables/home.teaser-left
    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/teasables/home.teaser-right
    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/teasables/home.teaser-bottom
    http://localhost:8080/caas/v1/coremedia/sites/caassiopeia-en-DE/articles/caas.article


### Enable a site for use with the headless server

1. Log in to CoreMedia Studio and open the *Site Indicator* document of the site

2. Note the *ID* property on the content tab.
   The value will be used for the `{siteId}` placeholder in the REST URLs

3. Go to the system tab end expand the *Local Settings* property box.
   1. Add a *String* property to it's root: 

      *Property:* tenantId   
      *Value:* your tenant identifier (only alphanumerical characters/lowercase)   

      This value will be used for the `{tenantId}` placeholder in the REST URLs

   2. Add a *Struct* property to it's root:
   
      *Property:* caasClients
      
      Add a sub *Struct* property (empty UUID):
      
      *Property*: 00000000-0000-0000-0000-000000000000
      
      Add a *String* property to the sub struct:
      
      *Property:* pd   
      *value:* default
      
      This maps all 'unknown' clients to the processing definition named 'default'. See the [wiki](https://github.com/CoreMedia/coremedia-headless-server/wiki/Processing-Definitions) for more information.
      
4. Try to fetch the JSON description of the enabled site:
    
    `http://localhost:8080/caas/v1/{tenantId}/sites/{siteId}`
    
    A response similar to the following one should be returned:
    ```json
    {
    "__typename": "CMSiteImpl",
    "__baseinterface": "CMSite",
    "_id": "coremedia:///cap/content/5838",
    "_name": "CaaS [Site]",
    "_type": "CMSite",
    "id": "caassiopeia-en-DE",
    "name": "CaaS Fragment Demo Site",
    "languageId": "en-DE"
    }
    ```
    
5. Test your site with the Swagger UI.

### Swagger

Test the API with your browser by calling the embedded Swagger UI: `http://localhost:8080/swagger-ui.html`.

#### Static documentation

During the test phase of the Maven build process HTML and PDF documentation is generated from the Swagger Specification. It can be accessed
from the running server at:

    http://localhost:8080/docs/index.html
    http://localhost:8080/docs/index.pdf
    
A JSON representation of the Swagger Specification for import in third party tools, i.e. Postman, is available at:

    http://localhost:8080/docs/swagger.json
