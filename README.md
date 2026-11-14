
# Belayer

1. Web API
1. Belayer Server Docker Image
1. Create Distributions
1. Installing Distributions


## 1. WebApi

### Requirement

* Java 17 to build
    * The compiler targets Java 11 (`options.release = 11` in `webapi/build.gradle`),
      so Java 11 or later is enough to run the server.
* jq
* Python 3.10 or later, as `/usr/bin/python3`
    * The `tgctl_w` / `tgha_w` scripts in `distribution/stub/` use the `match` statement, which Python 3.10 introduced.

### How to build

And build with gradle.

```console
$ cd webapi
$ ./gradlew clean build -x test
```

### How to execute

```console
$ cd webapi
$ java -jar build/libs/tsurugi-belayer.jar
```

Ctrl-C to shutdown.

### How to access

* API

    ```console
    $ curl http://localhost:8000/api/hello
    ```

* Health Check (on the management port, which is open unless `BELAYER_MANAGEMENT_ENABLED=false`)

    ```console
    $ curl http://localhost:18000/management/health
    ```

### Application Settings

You can configure the Belayer Server with environment variables.
See the env var table below.

|Environment Variable|Description|Default Value<br/>(used when variable is not defined)|
|:----|:----|:----|
|BELAYER_SERVER_PORT|server port for this web application.|`8000`|
|BELAYER_MANAGEMENT_PORT|management port for this web application. Not used when `BELAYER_MANAGEMENT_ENABLED` is `false`.|`18000`|
|BELAYER_MANAGEMENT_ENABLED|Set `false` to close the management port. It answers **without authentication**; what it serves is set by `BELAYER_MANAGEMENT_EXPOSURE`.|`true`|
|BELAYER_MANAGEMENT_EXPOSURE|Comma-separated endpoints served on the management port. The default serves health checks and metrics only. Add `info`, `env`, `beans`, `openapi` (the OpenAPI document) or `swagger-ui` for a while to investigate a problem; they show settings and internals and are not for production use.|`health,prometheus`|
|BELAYER_HOME|Installed directory.|`/usr/lib/tsurugi-belayer`|
|BELAYER_LOG_LEVEL|Log level for belaer WebAPI Server.|`WARN`|
|BELAYER_AUTH_ERROR_LOG_LEVEL|Log level for authentication/authorization error. Set WARN if you want to output logs when A&A error caused.|`ERROR`|
|BELAYER_ACCESS_LOG_ENABLED|Set `true` to out Jetty Access Log.|`false`|
|BELAYER_CONFIG_ROOT|The directory path to place Belayer config files.|`${BELAYER_HOME}/config`|
|BELAYER_ALLOWED_ORIGINS|The CORS setting for allowed origins. comma sparated strings.|``(means not permit CORS)|
|BELAYER_ALLOWED_METHODS|The CORS setting for allowed methods. comma sparated strings.|``(means not specify Methods)|
|BELAYER_ALLOWED_HEADERS|The CORS setting for allowed headers. comma sparated strings.|``(means not specify Headers)|
|BELAYER_ALLOW_CREDENTIALS|The CORS setting for allow credentials. (true or false) |`false`|
|BELAYER_FORWARD_HEADERS_STRATEGY|Set `framework` when the server is behind a reverse proxy, to take the URL the client requested from the `X-Forwarded-Host` / `X-Forwarded-Proto` / `X-Forwarded-Port` (or `Forwarded`) headers the proxy adds. *4|`none`(ignore these headers)|
|BELAYER_STORAGE_ROOT|The directory path to use as Belayer storage.|`/opt/belayer/storage`|
|TSURUGI_URL|URL to connect Tsurugi database.|`ipc:tsurugi`|
|TSURUGI_CONNECT_TIMEOUT_SEC|connect timeout seconds for Tsurugi database.|`5`|
|TSURUGI_SESSION_TIMEOUT_MIN|session timeout minutes for Tsurugi database.|`15`|
|TSURUGI_HOME|Tsurugi HOME directory.|`/usr/lib/tsurugi`|
|BELAYER_REQUIRED_JAVA_MAJOR_VERSION|Minimum Java major version the server checks for at startup.|`11`|
|TSURUGI_CONF|Tsurugi configuration file path.|`${TSURUGI_HOME}/var/etc/tsurugi.ini`|
|TSURUGI_TGCTL_COMMAND|Command used to start, stop and inspect Tsurugi database. *3|`${TSURUGI_HOME}/bin/tgctl_w`|
|BELAYER_TGCTL_DELEGATE|Read by `bin/tgctl_dispatch.sh`, not by the server: the command it passes the arguments on to. *3|`${TSURUGI_HOME}/bin/tgctl_w`|
|BELAYER_TSURUGIDB_UNIT|Read by `bin/tgctl_dispatch.sh`, not by the server: the systemd unit of the TsurugiDB. When set, the TsurugiDB is started with `sudo -n systemctl start` of this unit; `systemd/register_service.sh` sets it. *3|``(Belayer runs tgctl itself)|
|TSURUGI_TGHA_COMMAND|Command used for the HA operations (mode switch, database apply). *3|`${TSURUGI_HOME}/bin/tgha_w`|
|TSURUGI_AUTH_URL|URL for authentication server "harinoki".|`http://localhost:8080/harinoki`|
|TSURUGI_AUTH_AT_EXPIRATION_MIN|The expiration minutes for Access Token.|`10`|
|TSURUGI_AUTH_VERIFY_CACHE_MAX_MIN|Maximum minutes to reuse the result of verifying an Access Token with "harinoki" (never beyond the token's expiration). `0` verifies on every request.|`30`|
|TSURUGI_AUTH_VERIFY_TIMEOUT_MS|Maximum milliseconds to wait for "harinoki" to answer the verification of an Access Token. When it does not answer in time, the request is rejected with 401. `0` waits without limit.|`1000`|
|BELAYER_DEFAULT_ROLE_USER_MAPPING|Initial role-user mapping(JSON String) *1 *2|`{"ROLE_ADMIN": [".*"]}`|
|BELAYER_MAX_FILE_LIST_SIZE|Max size to list the files.|`500`|
|BELAYER_JOB_EXPIRATION_DAYS|Epiration days for the job history data.|`3`|
|BELAYER_DL_ZIP_COMPRESS_LEVEL|Zip compress level to download the all file contents in the specified directory.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_BK_ZIP_COMPRESS_LEVEL|Zip compress level to archive back up files.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_ADMIN_PAGE_ENABLED|Serve WebAdmin Contents.(optional, not supported as default)|`false`|
|BELAYER_ADMIN_PAGE_PATH|WebAdmin page path.|`/admin`|
|BELAYER_WEBADMIN_LOCATION|Path to WebAdmin Contents.(optional, not supported as default)|`file://dev/null`|

* *1: See [Belayer Authorizaion](./docs/belayer_authorization.md#ロールを持つユーザの指定例) document.
* *2: The user IDs are specified as regular expressions. The default value `{"ROLE_ADMIN": [".*"]}` therefore grants `ROLE_ADMIN` to **every authenticated user**. Be sure to set this variable explicitly for production use.
* *3: `tgctl_w` and `tgha_w` are the wrapper/stub scripts shipped in `distribution/stub/` and installed into `$TSURUGI_HOME/bin/` by `install.sh`. Point these variables at the official commands once they are available.
    * `install.sh` sets `TSURUGI_TGCTL_COMMAND` to `<install dir>/bin/tgctl_dispatch.sh` in `config/tsurugi-belayer.conf`. It decides how the TsurugiDB is started (by itself in a session of its own, or through the systemd unit in `BELAYER_TSURUGIDB_UNIT`) and passes everything else on to `BELAYER_TGCTL_DELEGATE`. Replace `tgctl_w` with `tgctl` there, not in `TSURUGI_TGCTL_COMMAND`.
    * The server runs these commands with one argument per value and passes the authentication token in the environment variable `TSURUGI_AUTH_TOKEN` rather than with `--auth-token`. A command used here has to take the token from that variable, as `tgctl` does.
    * This value is used only when the role-user mapping file (`${BELAYER_STORAGE_ROOT}/belayer_role_users.json`) does not exist. Once the mapping has been updated via the `/api/update/roleuser` API, the file takes precedence and changing this variable has no effect.
* *4: Behind a reverse proxy that sends its own address as `Host` (e.g. nginx with its default `proxy_set_header Host $proxy_host`), the server otherwise sees that address: requests from WebAdmin are rejected as cross-origin (403), and the Endpoints API (`/api/instance/list`) returns that address when `endpoints.csv` lists no endpoint. Configure the proxy to add the headers, for example with nginx:
    ```
    proxy_set_header X-Forwarded-Host  $http_host;
    proxy_set_header X-Forwarded-Proto $scheme;
    ```
    (`$http_host` is the `Host` the client sent, with the port if any.)
    Keep `none` when clients can reach the server directly, since they could then send these headers themselves. Without this setting, listing the public URL in `endpoints.csv` works around the Endpoints API part only.

### How to use mock Authentication for development

* Launch Java process with the profile option.
    * `java -Dspring.profiles.active=authmock -jar path/to/belayer.jar`
* You can use dummy users defined as uid=password in `webapi/src/main/resources/application-authmock.properties`


## 2. Belayer Server Docker Image


### How to build Belayer Server Docker Image

1. pull Tsurugi Docker image

```console
$ sudo docker pull ghcr.io/project-tsurugi/tsurugidb:latest
```

2. Start Server and Step in Docker Container.

```console
$ docker-compose up -d
$ docker-compose exec server bash
```

## 3. Create Distributions

### Requirement

See requirements to build each components.

* [WebAPI: Requirement](#requirement)

### How to create Belayer Distributions.

```console
$ cd distribution
$ sh create_archives.sh
```

The distribution files will be in the following directory.

* ```distribution/dist```

```console
$ ls dist
tsurugi-webapp-x.x.x.tar.gz
```

* tsurugi-webapp-x.x.x.tar.gz
    * WebAPI Server(without WebAdmin Page), installation scripts, etc.

## 4. Installing Distributions

### WebAPI

The installation has three steps. The third one is only for running Belayer as a systemd service.

| Step | Script | Run as | What it does |
|:--|:--|:--|:--|
| 1 | `install_packages.sh` | root | installs Java 11 or newer, `jq`, Python 3 and `curl` when missing (apt) |
| 2 | `install.sh` | the user that runs the TsurugiDB | lays out Belayer; registers no service |
| 3 | `<install dir>/systemd/register_service.sh` | root | registers Belayer and the TsurugiDB as systemd services (optional) |

#### 1. Packages

```console
$ tar -zxvf tsurugi-webapp-x.x.x.tar.gz
$ cd tsurugi-webapp-x.x.x
$ sudo sh install_packages.sh
```

#### 2. Lay out Belayer

Run `install.sh` as the OS user that runs the TsurugiDB; it does not need root. Give the TsurugiDB with `TSURUGI_HOME` or `--tsurugi-home`.

```console
$ sh install.sh --tsurugi-home=$HOME/opt/tsurugi
$ ls $HOME/opt/tsurugi-belayer-x.x.x
bin  config  jar  proc  storage  systemd
```

By default `tsurugi-belayer-x.x.x` is created beside the TsurugiDB (`/usr/lib/tsurugi-belayer-x.x.x` for `/usr/lib/tsurugi`).
Options: `--tsurugi-home=<dir>`, `--tsurugi-conf=<file>`, `--prefix=<dir>`, `--storage=<dir>` (default `<install dir>/storage`), `--port=<port>` (default `8000`) and `--management-port=<port>` (default `18000`); see `install.sh --help`.
They are written to `config/tsurugi-belayer.conf`, together with `TSURUGI_URL` taken from `[ipc_endpoint] database_name` of the configuration of the TsurugiDB.
A re-install of the same version keeps the files already in `config/`.

Execute `<install dir>/bin/start_server.sh` to start belayer server.
It reads `config/tsurugi-belayer.conf` and passes all the variables in it to the server.
The values in the conf take precedence over the environment variables of the calling shell.
A TsurugiDB started through Belayer runs in a session and process group of its own, so stopping Belayer (Ctrl-C, closing the terminal) leaves it running.

#### 3. Run Belayer as a systemd service

```console
$ sudo $HOME/opt/tsurugi-belayer-x.x.x/systemd/register_service.sh
```

**When Belayer runs as a systemd service, the TsurugiDB runs as one as well.** A TsurugiDB that Belayer started inside the service of Belayer would stop together with it (stop, restart, crash). `register_service.sh` registers both, and makes Belayer start the TsurugiDB with `sudo systemctl start` of its unit (`BELAYER_TSURUGIDB_UNIT`).

* Both run as the owner of the install directory, which has to own the TsurugiDB as well.
* The units are `tsurugi-belayer-<name>.service` and `tsurugidb-<name>.service` (`tsurugi-auth-<name>.service` with `--with-auth-server`). `<name>` is given by `--name` and defaults to the owner (no name for root: `tsurugi-belayer.service`, ...).
* The unit of the authentication server counts as started once `[authentication] url` of `tsurugi.ini` (default `http://localhost:8080/harinoki`) answers, so that a TsurugiDB with authentication enabled can start right after it.
* An owner other than root may run `/usr/bin/systemctl start tsurugidb-<name>.service` without a password (`/etc/sudoers.d/tsurugi-belayer-<name>`), and nothing else.
* The service of Belayer is only ordered after the one of the TsurugiDB: starting or stopping one does not start or stop the other.
* `--tsurugidb-only` registers the TsurugiDB alone, for a Belayer started by hand. Registering Belayer alone is not supported.

```sh
sudo systemctl start tsurugidb-<name>.service
sudo systemctl start tsurugi-belayer-<name>.service
```

`sudo <install dir>/systemd/unregister_service.sh` removes the services again (it stops with an error while the TsurugiDB runs, unless `--stop-tsurugidb` is given). It leaves the services alone when the service of Belayer runs another install, e.g. after an upgrade.
```
