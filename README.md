
# Belayer

1. Web API
1. Belayer Server Docker Image
1. Create Distributions
1. Installing Distributions


## 1. WebApi

### Requirement

* Java11

### How to build

Define two environment variables.

```console
$ export GPR_USER=<YOUR_GITHUB_ACCOUNT_NAME>
$ export GPR_KEY=<YOUR_PERSONAL_ACCESS_TOKEN_VALUE>
```

And build with gradle.

```console
$ cd webapi
$ ./gradlew clean build -x test
```

### How to execute

```console
$ cd webapi
$ java -jar build/libs/belayer-webapi-0.0.1-SNAPSHOT.jar
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
|BELAYER_WEBADMIN_LOCATION|Location for Belayer WebAdmin contents.|`file://usr/lib/tsurugi-webapp/webadmin/`|
|BELAYER_LOG_LEVEL|Log level for belaer WebAPI Server.|`WARN`|
|BELAYER_STORAGE_ROOT|The directory path to use as Belayer storage.|`/opt/belayer/storage`|
|TSURUGI_URL|URL to connect Tsurugi database.|`tcp://localhost:12345/`|
|TSURUGI_CONNECT_TIMEOUT_SEC|connect timeout seconds for Tsurugi database.|`5`|
|TSURUGI_SESSION_TIMEOUT_MIN|session timeout minutes for Tsurugi database.|`15`|
|TSURUGI_HOME|Tsurugi HOME directory.|`/usr/lib/tsurugi`|
|TSURUGI_CONF|Tsurugi configuration file path.|`${TSURUGI_HOME}/conf/tsurugi.ini`|
|TSURUGI_AUTH_URL|URL for authentication server "harinoki".|`http://localhost:8080/harinoki`|
|TSURUGI_AUTH_AT_EXPIRATION_MIN|The expiration minutes for Access Token.|`10`|
|BELAYER_MAX_FILE_LIST_SIZE|Max size to list the files.|`500`|
|BELAYER_JOB_EXPIRATION_DAYS|Epiration days for the job history data.|`3`|
|BELAYER_DL_ZIP_COMPRESS_LEVEL|Zip compress level to download the all file contents in the specified directory.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_BK_ZIP_COMPRESS_LEVEL|Zip compress level to archive back up files.<br/>(0-9 or -1 as default)|`-1`(default compress level)|
|BELAYER_ADMIN_PAGE_ENABLED|Serve WebAdmin Contents.(optional, not supported as default)|`false`|
|BELAYER_ADMIN_PAGE_PATH|WebAdmin page path.|`/admin`|
|BELAYER_WEBADMIN_LOCATION|Path to WebAdmin Contents.(optional, not supported as default)|`file://dev/null`|

### How to use mock Authentication

* Build Belayer JAR with the option `-Dtsubakuro-auth`.
    * `./gradlew build -x test -Dtsubakuro-auth=mock`
* Launch Java process with the profile option.
    * `java -Dspring.profiles.active=authmock -jar path/to/belayer.jar`
* You can use dummy users defined as uid=password in `webapi/src/main/resources/application-authmock.properties`


## 2. Belayer Server Docker Image


### How to build Belayer Server Docker Image

1. pull Tsurugi Docker image

Create `Personal Access Token(PAT)` for GitHub Container Registory.
Firstly, set PAT Value as ENV `CR_PAT` then docker login to ghcr.io.
Secondly, pull Tsurugi Docker Image.

```console
$ echo $CR_PAT | sudo docker login ghcr.io -u my_user_name --password-stdin
$ sudo docker pull ghcr.io/project-tsurugi/tsurugi:latest
```

2. Define environment variables

```console
$ export GPR_USER=<YOUR_GITHUB_ACCOUNT_NAME>
$ export GPR_KEY=<YOUR_PERSONAL_ACCESS_TOKEN_VALUE>
```

3. Start Server and Step in Docker Container.

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
tsurugi-webapp-0.0.1.tar.gz
```

* tsurugi-webapp-x.x.x.tar.gz
    * WebAPI (included without WebAdmin Page) installation scripts, etc.

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

Execute `/usr/lib/tsurugi-webapp-x-x-x/bin/start_server.sh` to start belayer server.

#### Install to another directory.

You can specify install directory using `--prefix` option.

```console
$ tar -zxvf tsurugi-webapp-x.x.x.tar.gz
$ cd tsurugi-webapp-x.x.x
$ sh install.sh --prefix=$HOME/tsurugi-webapp
$ ls $HOME/tsurugi-webapp/*
tsurugi-webapp-x-x-x
```

Execute `<your_prefix>/bin/start_server.sh` to start belayer server.



