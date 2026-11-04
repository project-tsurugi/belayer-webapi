# Change Log

## ver 1.5.3 (Since ver 1.5.2)

### Breaking changes

* rename the property for the minimum Java major version checked at startup, from `webapi.required.java_majar_version` to `webapi.required.java_major_version`(maj**a**r -> maj**o**r), and add the environment variable `BELAYER_REQUIRED_JAVA_MAJOR_VERSION` to override it.
    * **A setting given under the old name (e.g. `WEBAPI_REQUIRED_JAVA_MAJAR_VERSION`) is no longer read, and the default `11` applies without any warning.** Use `BELAYER_REQUIRED_JAVA_MAJOR_VERSION` instead.
* `POST /api/transaction/status/{transaction_id}` now returns 400 (`Invalid parameter. Specify commit/rollback.`). It reached the Finish Transaction API (`POST /api/transaction/{type}/{transaction_id}`), which accepted `status` as `{type}` and returned the transaction status without finishing it. Use the Show Transaction Status API (`GET /api/transaction/status/{transaction_id}`), which is not changed.
    * WebAdmin and RemoteCLI do not call these APIs.
* the management port (`BELAYER_MANAGEMENT_PORT`, default 18000) now serves only `health` and `prometheus` by default. It served the actuator endpoints `health`, `info`, `beans`, `env` and `prometheus`, the OpenAPI document and Swagger UI, all without authentication.
    * `BELAYER_MANAGEMENT_EXPOSURE` (comma-separated, default `health,prometheus`) lists what it serves. Add `info`, `env`, `beans`, `openapi` or `swagger-ui` for a while when needed; they are not for production use. With `openapi` or `swagger-ui`, the URLs are `/management/openapi/springdocDefault` and `/management/swagger-ui`.
    * the port stays open by default, for the metrics. `BELAYER_MANAGEMENT_ENABLED=false` closes it.

### Bug fixes

* **[Security]** fix the Restore API (`POST /api/restore`, `zipFilePath`) and the Load API (`POST /api/load/{table}`, `files`) to reject a path that points outside the user's storage directory with 400. A path with `../` could restore from any zip file the server can read, load (and then dump) another user's files, and tell whether a file exists. The check is now shared by all file paths given to the APIs.
* stop writing the authentication token in plain text to files and logs.
    * `show_db_status.sh` wrote its arguments, including the token, to `/tmp/show_db_status_out.log` on every call. It now writes the log only when Belayer logs at DEBUG, with the token masked, and creates the file with mode 600.
    * the stubs `tgctl_w` and `tgha_w` wrote every command line, including `--auth-token`, to `/tmp/tgctl_w_history.log` and `/tmp/tgha_w_history.log`. They now write the history only when Belayer logs at DEBUG, with the token masked, and create the files with mode 600.
    * the command lines of the external commands written to the DEBUG log of Belayer now have the token masked.
    * Note: `com.tsurugidb` logs at DEBUG unless `BELAYER_LOG_LEVEL` is set.
* fix the rollback of a stateful transaction (`POST /api/transaction/rollback/{transaction_id}`), which always returned 400 and left the transaction open until it timed out.
* return 400 instead of 500 for missing items in the requests of the file APIs.
    * delete file / delete directory without `path`, delete files without `pathList` or with `null` in it (nothing is deleted then), download zip without a body, without `pathList` or with `null` in it.
    * `destDir` of the Upload API is now optional; without it, the files are saved directly under the user's storage directory (as with `""`).
* fix the Download Zip API (`POST /api/downloadzip`) failing with 500 for files directly under the user's storage directory (e.g. `a.parquet`), as WebAdmin sends them when such files are selected. Paths such as `./x/a` and `x/b` are now also taken as the same directory.
* return 400 instead of 200 with no body, doing nothing, when the request body is empty, for the Session Variable Setting API, the Session Kill API and the Role-User Mapping Update API.
* fix the job list file (`belayer_jobs.json`) handling.
    * it was rewritten on every read of a job, in place, so a crash while writing it lost the job history at the next start. It is now written in the background when jobs change (and every 5 seconds for progress), and replaced atomically. The format is unchanged.
    * fix a deadlock between canceling a job and a job finishing at the same time.
    * a failure to write the file is now logged as a warning and no longer fails the API call.
* release resources that were left behind.
    * restore and offline backup did not stop watching their monitoring file, which kept a file watch and the job in memory, and could overwrite the progress of a finished job.
    * the DB Status API (also called when a backup or a restore starts) left a file `monitoring-*.log` under `${java.io.tmpdir}/belayer-db-status` on every call.
    * close file streams that were left open until garbage collection.
* fix `start_server.sh` so that the settings in `tsurugi-belayer.conf` take effect.
    * export `CONF_FOLDER`. Without it, the launch script in the JAR could not locate `tsurugi-belayer.conf`.
    * read `tsurugi-belayer.conf` and export all its variables before launching the JAR, as systemd does with `EnvironmentFile`. The launch script in the JAR sources the conf without exporting it, so only `JAVA_OPTS` and `RUN_ARGS` took effect, and settings such as `BELAYER_LOG_LEVEL` were ignored.
    * check `TSURUGI_HOME` after reading the conf, so that it no longer has to be set in the calling shell. The value in the conf takes precedence over the calling shell.
    * `start_server.sh` is a fallback script to launch manually. The systemd service does not use it and is not affected.
* `install.sh` now puts `${TSURUGI_HOME}/bin/mode.txt` (containing `standalone`) next to the stubs `tgctl_w` and `tgha_w`. Without it, until a DB start through Belayer wrote it, the DB Status API returned the mode `stop`, e.g. right after TsurugiDB was started in another way. An existing file is kept.
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
* describe every route in the OpenAPI document (operation, parameters, request body, responses and the required permission), and give it the version of Belayer instead of `unspecified`. The items of the request bodies that the server sets by itself (e.g. `uid`) are no longer shown as inputs.
* document the requirements and limits.
    * README: Python 3.10 or later is required by the stubs `tgctl_w` and `tgha_w` (they use the `match` statement).
    * the WebAPI document: the DB Start API and the DB Change Mode API accept only `standalone` as the mode.
* update `docs/belayer_authorization.md` to the permissions in the code: `P_STREAM_API` (not `P_STREAM`), `P_LIST_ENDPOINTS`, `P_DB_CHANGE_MODE` and `P_DB_SYNC`, the role x permission table and the `/api/list/roles` example.
* write the Javadoc of the public API so that the javadoc of JDK 17 reports no warnings or errors, and remove `javadoc.failOnError = false`, so that a Javadoc error now stops the build.
* clean up the code: remove unused classes and arguments, make constants final, fix names.
* the tests pin the Spring profile `test-group`, so that `SPRING_PROFILES_ACTIVE` in the environment no longer breaks them.
* the release workflow publishes a tag ending with `-snapshot` or containing `-beta` (in any case) as a prerelease, not as the latest release.
* add a VS Code dev container (`.devcontainer`) on the official TsurugiDB image.

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
