# API Key Steward

## What is API Key Steward?

API Key Steward provides API keys management and validation solutions.
It emphasizes seamless integration with Identity Providers, like for e.g. Okta/Auth0.

## Installation

For test/development setups it is recommended to use Docker and Docker Compose (version 3).

For the time being, images are not published anywhere. Therefore, you have to build them yourself, although it is quite simple. 

### Build Docker image

Open terminal in the main folder of the project and build a new Docker image:
```docker
docker build -t api-key-steward .
```

### JWT Configuration
 
JWT and JWKS settings have to be configured in order to start up the service. These are provided via environment variables:

- `AUTH_JWKS_URL` - URL to download JSON Web Key Set from, for e.g. Auth0 provides JWKS under: `https://<your-tenant-id>.auth0.com/.well-known/jwks.json`
- `AUTH_JWT_ISSUER` - This is your trusted issuer of the JWT (`iss` claim inside JWT). For Auth0 the variable should be: `https://<your-tenant-id>.auth0.com/`
- `AUTH_JWT_AUDIENCE` - This is the audience this JWT is created for (one of `aud` claim values inside JWT). The JWT has to contain the audience provided here, but can also contain others.

*Note: Only a single issuer and audience is currently supported with the use of environment variables. If you need to configure more, provide additional variables in [resourceServer.conf](src/main/resources/resourceServer.conf) file under `auth.jwt` settings. Also add them to [docker-compose.yaml](docker-compose.yaml) file.*

### JWT Permissions

All endpoints (except API key validation one) require a JSON Web Token with `permissions` claim, which is an array of strings. You have to configure your Identity Provider to add these into your JWT. 

Just like endpoints, permissions are divided into 2 categories: `admin` and `user`.

The currently used permissions are:
- `steward:read:admin` - required for all GET admin endpoints.
- `steward:write:admin` - required for all other admin endpoints (POST, PUT, DELETE).


- `steward:read:apikey` - required for all GET user endpoints.
- `steward:write:apikey` - required for all other user endpoints (POST and DELETE).

So your decoded admin JWT should contain the following claim:
```json
{
    ...
    "permissions": [
      "steward:read:admin",
      "steward:write:admin"
    ]
}
```

### Startup

Once the previous steps are done, you can run Docker Compose to start the service. In the main folder of the project run:
```shell
docker-compose up
```

This should start 2 docker containers (postgres and api-key-steward). In service's logs you should be able to see the configuration used, and some info about DB migrations. The service listens on port `:8080`.

### API Documentation

After starting the service, you can access its API documentation in OpenAPI standard. Simply run one of the following command:
```shell
curl http://localhost:8080/docs/v0/docs.yaml >> OpenAPISpec.yaml
```
or
```shell
curl http://localhost:8080/docs/v0/docs.json >> OpenAPISpec.json
```

Each of these should create a new file in your current directory.
You can then copy the contents of this file and use it on a page like https://editor.swagger.io/ to create a human-readable documentation of `api-key-steward` API.

## Security

See [SECURITY.md](SECURITY.md)
