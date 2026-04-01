# Change Log

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
