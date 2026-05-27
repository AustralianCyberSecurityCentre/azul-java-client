# Azul Java Client

Azul Java Client is a near complete Java client for Azul's REST API.

Interact with Azul using your terminal instead of clicking in the UI a thousand times!

Tested on Ubuntu 22.04 with Java 17.

## Build

```bash
mvn package
```

This produces a shaded (fat) JAR at `target/azul-java-client-1.0.0.jar`.

## Setup

Azul Java Client requires a config file located at `~/.azul.ini`.

A default config will be generated on first run.

You will need to adjust the config options as appropriate.

```ini
[default]
azul_url = http://localhost
oidc_url = http://keycloak/.well-known/openid-configuration
auth_type = callback
auth_scopes =
auth_client_id = azul-web
auth_client_secret =
azul_verify_ssl = true
auth_token = {}
auth_token_time = 0
max_timeout = 300.0
oidc_timeout = 10.0
```

## Usage

```bash
java -jar target/azul-java-client-1.0.0.jar --help
```

Use `-c <section>` to switch between named config sections (e.g. `-c dev`).

## Running Tests

### Unit tests

Run the unit test suite with Maven:

```bash
mvn test
```

