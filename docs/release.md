# Change Log

## ver 1.5.3 (Since ver 1.5.2)

### Breaking changes

* rename the property for the minimum Java major version checked at startup, from `webapi.required.java_majar_version` to `webapi.required.java_major_version`(maj**a**r -> maj**o**r), and add the environment variable `BELAYER_REQUIRED_JAVA_MAJOR_VERSION` to override it.
    * **A setting given under the old name (e.g. `WEBAPI_REQUIRED_JAVA_MAJAR_VERSION`) is no longer read, and the default `11` applies without any warning.** Use `BELAYER_REQUIRED_JAVA_MAJOR_VERSION` instead.
* `POST /api/transaction/status/{transaction_id}` now returns 400 (`Invalid parameter. Specify commit/rollback.`). It reached the Finish Transaction API (`POST /api/transaction/{type}/{transaction_id}`), which accepted `status` as `{type}` and returned the transaction status without finishing it. Use the Show Transaction Status API (`GET /api/transaction/status/{transaction_id}`), which is not changed.
    * WebAdmin and RemoteCLI do not call these APIs.

### Bug fixes

* fix `start_server.sh` so that the settings in `tsurugi-belayer.conf` take effect.
    * export `CONF_FOLDER`. Without it, the launch script in the JAR could not locate `tsurugi-belayer.conf`.
    * read `tsurugi-belayer.conf` and export all its variables before launching the JAR, as systemd does with `EnvironmentFile`. The launch script in the JAR sources the conf without exporting it, so only `JAVA_OPTS` and `RUN_ARGS` took effect, and settings such as `BELAYER_LOG_LEVEL` were ignored.
    * check `TSURUGI_HOME` after reading the conf, so that it no longer has to be set in the calling shell. The value in the conf takes precedence over the calling shell.
    * `start_server.sh` is a fallback script to launch manually. The systemd service does not use it and is not affected.
* fix the Backup/Restore Cancel API (`/api/br/cancel`) to accept `P_BACKUP` as well as `P_RESTORE`, as `docs/belayer_authorization.md` states. A user holding only `ROLE_BACKUP` could start a backup job but could not cancel it (403).
* narrow the path match of the Finish Transaction API from `/api/transaction/**` to `/api/transaction/{type}/{transaction_id}`, so that it does not take over the permission settings of the other APIs under `/api/transaction/`. <u>This does not change the current behavior</u>, since all of them require `P_STREAM_API`.

### Misc changes

* build with JDK 17 (Gradle toolchain) while keeping Java 11 as the target and the runtime requirement. CI also uses JDK 17.
* update README.md.
    * state that building requires Java 17 and running requires Java 11 or later.
    * list `TSURUGI_TGCTL_COMMAND` and `TSURUGI_TGHA_COMMAND` in the environment variable table.
    * correct the default value of `BELAYER_DEFAULT_ROLE_USER_MAPPING` to `{"ROLE_ADMIN": [".*"]}`, and note that it grants `ROLE_ADMIN` to every authenticated user unless set explicitly.
    * correct the JAR name to `tsurugi-belayer.jar`.
* fix the key name in the Instance List API example in the WebAPI document (`endpoinsts` -> `endpoints`).
* fix the section links in the getting started document.

### Full Changelog

https://github.com/project-tsurugi/belayer-webapi/compare/1.5.2...1.5.3

## ver 1.5.2 (Since ver 1.5.1)

### Misc changes

* update tsubakuro dependencies to version 1.16.0.

## ver 1.5.1 (Since ver 1.5.0)

### Misc changes

* add the return value into the error messages when show_db_status.sh failed.

### Bug fixes

* fix Belayerinitilizer and BelayerShellScriptInitializer in spcified profiles such as authmock, test-group.
* fix stub to use TSURUGI_HOME env variable.

### Full Changelog

https://github.com/project-tsurugi/belayer-webapi/compare/1.5.0...1.5.1

## ver 1.5.0 (Since ver 1.4.8)

Since [ver 1.4.7](https://github.com/project-tsurugi/belayer-webapi/releases/tag/1.4.7)

### Breaking changes

* Some new APIs and extended APIs for multi-node feature. 
    * [New] Endpoint List API（エンドポイント一覧取得API）
    * [New] Db ChangeMode API（DB起動モード変更API）
    * [New] Db Sysnc API（DB同期API）
    * [Changed] DbStatusAPI（DBステータス確認API）: add field item into output.  
* **belayer-webapi now requires `jq` and `python3` command.**
* change default setting for CORS, from all allowed to all deny as default.
* enable to control access log by environment variables.
* enable to output logs when authentication/authorization failed.
* introduce BELAYER_HOME and directory to place config files which includes `endpoint.csv` and `instance_info.json`.
* change JAR name from tsurugi-webapp-xxx.jar to tsurugi-belayer.jar
* organize spring application.properites and profiles.
* introduce springbooot launch script which is automatically included in JAR and the systemd service setting follows it.
* add stub scirpts for tgctl, tgha which provides dummy multi-nodes feature. **These script requires `pyton3`.**
* add initilalizer funciton to check Java version and to check requied commands.
* add initializer function to place shell scripts into `proc` directory.

### Misc changes

* update dependencies.
* update artifacts for CI/CD. 

### Full Changelog

https://github.com/project-tsurugi/belayer-webapi/compare/1.4.7...1.5.0
