
# Belayer

1. Web API
1. Belayer Server Docker Image
1. Create Distributions
1. Installing Distributions


## 1. WebApi

### Requirement

* Java11

### How to build

And build with gradle.

```console
$ cd webapi
$ ./gradlew clean build -x test
```

### How to execute

```console
$ cd webapi
$ java -jar build/libs/belayer-webapi-x.x.x.jar
```

Ctrl-C to shutdown.

### How to access

* API

    ```console
    $ curl http://localhost:8000/api/hello
    ```

* Health Check

    ```console
    $ curl http://localhost:18000/management/health
    ```

### Application Settings

You can configure the Belayer Server with environment variables.
See the env var table below.

|Environment Variable|Description|Default Value<br/>(used when variable is not defined)|
|:----|:----|:----|
|BELAYER_SERVER_PORT|server port for this web application.|`8000`|
|BELAYER_MANAGEMENT_PORT|management port for this web application.|`18000`|
|BELAYER_HOME|Installed directory.|`/usr/lib/tsurugi-belayer`|
|BELAYER_LOG_LEVEL|Log level for belaer WebAPI Server.|`WARN`|
|BELAYER_CONFIG_ROOT|The directory path to place Belayer config files.|`/opt/belayer`|
|BELAYER_ALLOWED_ORIGINS|The CORS setting for allowed origins. comma sparated strings.|``(means not permit CORS)|
|BELAYER_ALLOWED_METHODS|The CORS setting for allowed methods. comma sparated strings.|``(means not specify Methods)|
|BELAYER_ALLOWED_HEADERS|The CORS setting for allowed headers. comma sparated strings.|``(means not specify Headers)|
|BELAYER_ALLOW_CREDENTIALS|The CORS setting for allow credentials. (true or false) |`false`|
|BELAYER_STORAGE_ROOT|The directory path to use as Belayer storage.|`/opt/belayer/storage`|
|TSURUGI_URL|URL to connect Tsurugi database.|`ipc:tsurugi`|
|TSURUGI_CONNECT_TIMEOUT_SEC|connect timeout seconds for Tsurugi database.|`5`|
|TSURUGI_SESSION_TIMEOUT_MIN|session timeout minutes for Tsurugi database.|`15`|
|TSURUGI_HOME|Tsurugi HOME directory.|`/usr/lib/tsurugi`|
|TSURUGI_CONF|Tsurugi configuration file path.|`${TSURUGI_HOME}/var/etc/tsurugi.ini`|
|TSURUGI_AUTH_URL|URL for authentication server "harinoki".|`http://localhost:8080/harinoki`|
|TSURUGI_AUTH_AT_EXPIRATION_MIN|The expiration minutes for Access Token.|`10`|
|BELAYER_DEFAULT_ROLE_USER_MAPPING|Initial role-user mapping(JSON String) *1|`{"ROLE_ADMIN": ["tsurugi"]}`|
|BELAYER_MAX_FILE_LIST_SIZE|Max size to list the files.|`500`|
|BELAYER_JOB_EXPIRATION_DAYS|Epiration days for the job history data.|`3`|
|BELAYER_DL_ZIP_COMPRESS_LEVEL|Zip compress level to download the all file contents in the specified directory.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_BK_ZIP_COMPRESS_LEVEL|Zip compress level to archive back up files.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_ADMIN_PAGE_ENABLED|Serve WebAdmin Contents.(optional, not supported as default)|`false`|
|BELAYER_ADMIN_PAGE_PATH|WebAdmin page path.|`/admin`|
|BELAYER_WEBADMIN_LOCATION|Path to WebAdmin Contents.(optional, not supported as default)|`file://dev/null`|

* *1: See [Belayer Authorizaion](./docs/belayer_authorization.md#ロールを持つユーザの指定例) document.

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

#### Install to the default directory.

```console
$ tar -zxvf tsurugi-webapp-x.x.x.tar.gz
$ cd tsurugi-webapp-x.x.x
$ sudo sh install.sh
```

Belayer Web API Server is installed under `/usr/lib`.

```console
$ ls /usr/lib/tsurugi-webapp-*
tsurugi-webapp-x-x-x
```
Specify TsurugiDB installed path as `TSURUGI_HOME` environment variable, 
then execute `/usr/lib/tsurugi-webapp-x-x-x/bin/start_server.sh` to start belayer server.

#### Install to another directory.

You can specify install directory using `--prefix` option.

```console
$ tar -zxvf tsurugi-webapp-x.x.x.tar.gz
$ cd tsurugi-webapp-x.x.x
$ sh install.sh --prefix=$HOME/tsurugi-webapp
$ ls $HOME/tsurugi-webapp/*
tsurugi-webapp-x-x-x
```

Specify TsurugiDB installed path as `TSURUGI_HOME` environment variable, 
then execute `<your_prefix>/bin/start_server.sh` to start belayer server.

#### Execute belayer server as Systemd service

Copy service definition file to `/etc/systemd/system`.

```sh
sudo cp tsurugi-webapp.service /etc/systemd/system
sudo systemctl enable tsurugi-webapp.service
```

**Note: If you have changed the directory to be installed belayer, you have to apply your setting to the `TSURUGI_HOME` path and the `ExecStart` path in `tsurugi-webapp.service`.**

tsurugi-webapp.service

```text
[Service]
Type=simple
Environment=TSURUGI_HOME=/usr/lib/tsurugi
ExecStart=/usr/lib/tsurugi-webapp-#VERSION#/bin/start_server.sh
Restart=no
```