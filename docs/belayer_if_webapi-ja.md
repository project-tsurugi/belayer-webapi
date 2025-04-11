**目次**

- [Belayer Web API インタフェース仕様](#belayer-web-api-インタフェース仕様)
  - [API共通仕様](#api共通仕様)
  - [ユーザ認証API](#ユーザ認証api)
  - [トークンリフレッシュAPI](#トークンリフレッシュapi)
  - [ファイルアップロードAPI](#ファイルアップロードapi)
  - [ファイルダウンロードAPI](#ファイルダウンロードapi)
  - [ファイル一括ダウンロードAPI](#ファイル一括ダウンロードapi)
  - [ファイル削除API](#ファイル削除api)
  - [ファイル複数削除API](#ファイル複数削除api)
  - [ディレクトリ削除API](#ディレクトリ削除api)
  - [ディレクトリ一覧取得API](#ディレクトリ一覧取得api)
  - [バックアップ実行指示API](#バックアップ実行指示api)
  - [データリストア実行指示API](#データリストア実行指示api)
  - [バックアップ／リストア一覧取得API](#バックアップリストア一覧取得api)
  - [バックアップ／リストア実行ステータス取得API](#バックアップリストア実行ステータス取得api)
  - [バックアップ／リストアキャンセルAPI](#バックアップリストアキャンセルapi)
  - [ダンプ取得API](#ダンプ取得api)
  - [データロードAPI](#データロードapi)
  - [ダンプ／ロード一覧取得API](#ダンプ／ロード一覧取得api)
  - [ダンプ／ロード実行ステータス取得API](#ダンプ／ロード実行ステータス取得api)
  - [ダンプ／ロードキャンセルAPI](#ダンプ／ロードキャンセルapi)
  - [トランザクション開始API](#トランザクション開始api)
  - [トランザクションコミット/ロールバックAPI](#トランザクションコミットロールバックapi)
  - [トランザクション確認API](#トランザクション確認api)
  - [ストリームデータダンプAPI](#ストリームデータダンプapi)
  - [ストリームデータロードAPI](#ストリームデータロードapi)
  - [セッションステータス確認API](#セッションステータス確認api)
  - [セッション変数設定API](#セッション変数設定api)
  - [セッション停止API](#セッション停止api)
  - [DB起動API](#db起動api)
  - [DB停止API](#db停止api)
  - [DBステータス確認API](#dbステータス確認api)
  - [テーブル名一覧取得API](#テーブル名一覧取得api)
- [ファイルフォーマット](#ファイルフォーマット)
  - [ダンプファイルCSVフォーマット](#ダンプファイルcsvフォーマット)
- [補足事項](#補足事項)
  - [ログ出力](#ログ出力)
  - [ジョブ情報の永続化](#ジョブ情報の永続化)
  - [テンポラリファイル](#テンポラリファイル)

# Belayer Web API インタフェース仕様

## API共通仕様

* リクエスト
   * ヘッダー（ユーザ認証API以外）
       * Authorization
           * Bearer: [ACCESS_TOKEN_VALUE]
               * [ACCESS_TOKEN_VALUE]には、ユーザ認証APIから受け取ったアクセストークンを設定する。

## ユーザ認証API

* 概要: ユーザID,パスワードを使ってユーザ認証を行い、認証成功時はアクセストークンを返却する。
* リクエスト
    * メソッド:POST
    * パス: /api/auth
    * パラメータ:なし
    * Content-Type: application/x-www-form-urlencoded
    * ボディ:
        * uid: ユーザID(必須)
        * pw: パスワード(必須)
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * userId: User ID
            * refreshToken: リフレッシュトークン
            * refreshExpirationTime: リフレッシュトークンの有効期限日時
            * accessToken: アクセストークン
            * accessExpirationTime: アクセストークンの有効期限日時
            * errorMessage: 常にnull

            ```
            {
              "userId": "user1",
              "refreshToken":"[REFRESH_TOKEN_VALUE]",
              "refreshExpirationTime":"2022-12-09T10:05:40Z",
              "accessToken":"[ACCESS_TOKEN_VALUE]",
              "accessExpirationTime":"2022-12-09T10:05:40Z",
              "errorMessage":null
            }
            ```

    * 異常(認証エラー)
        * 条件
            * ユーザID、パスワードのいずれか（もしくは両方）がブランクまたは未指定
            * ユーザIDとパスワード組み合わせが不一致
        * ステータスコード: 400
        * ボディ:
            * userId: User ID
            * refreshToken: 常にnull
            * refreshExpirationTime: 常にnull
            * accessToken: 常にnull
            * accessExpirationTime: 常にnull
            * errorMessage: エラーメッセージ

            ```
            {
              "userId": "user1",
              "refreshToken": null,
              "refreshExpirationTime": null,
              "accessToken": null,
              "accessExpirationTime": null,
              "errorMessage": "Authentication Error."
            }
            ```

## トークンリフレッシュAPI

* 概要: リフレッシュトークンを使って新しいアクセストークンを生成する。
* リクエスト
    * メソッド:POST
    * パス: /api/refresh
    * パラメータ:なし
    * Content-Type: application/x-www-form-urlencoded
    * ボディ:
        * rt: リフレッシュトークン(必須)
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * userId: User ID
            * refreshToken: リフレッシュトークン
            * refreshExpirationTime: リフレッシュトークンの有効期限日時
            * accessToken: アクセストークン
            * accessExpirationTime: アクセストークンの有効期限日時
            * errorMessage: 常にnull

            ```
            {
              "userId": "user1",
              "refreshToken":"[REFRESH_TOKEN_VALUE]",
              "refreshExpirationTime":"2022-12-09T10:05:40Z",
              "accessToken":"[ACCESS_TOKEN_VALUE]",
              "accessExpirationTime":"2022-12-09T10:05:40Z",
              "errorMessage":null
            }
            ```

    * 異常(リフレッシュエラー)
        * 条件
            * リフレッシュトークンが不正
            * リフレッシュトークンが期限切れ
        * ステータスコード: 400
        * ボディ:
            * userId: User ID
            * refreshToken: 常にnull
            * refreshExpirationTime: 常にnull
            * accessToken: 常にnull
            * accessExpirationTime: 常にnull
            * errorMessage: エラーメッセージ

            ```
            {
              "userId": "user1",
              "refreshToken": null,
              "refreshExpirationTime": null,
              "accessToken": null,
              "accessExpirationTime": null,
              "errorMessage": "Refresh token is expired."
            }
            ```

## ファイルアップロードAPI

* 概要: ファイルを受け取り、指定のファイルパスに保存する。
* リクエスト
    * メソッド:POST
    * パス: /api/upload
    * パラメータ
        * なし
    * Content-Type: multipart/form-data
    * ボディ:
        * マルチパート形式
            * FilePart: アップロードするファイル(必須、複数指定可能）
                * header
                    * Content-Type: application/octet-stream
                    * Content-Disposition: form-data; name="file"; filename="path/to/real/file.parquet"
                        * name: 常に"file"(必須)
                        * filename: 保存するファイルのローカルファイルパス(必須)
                * body
                    * ファイルコンテンツ(必須)
            * FormFieldPart: 保存先ディレクトリ(必須)
                * header
                    * Content-Disposition: form-data; name=“destDir”
                    * name: 常に“destDir”(必須)
                * body
                    * アップロードファイルの保存ディレクトリ。STRAGE_DIR/[uid]からの相対パス(必須)
            * FormFieldPart: 上書き可否。未指定時は上書き不可。(任意)
                * header
                    * Content-Disposition: form-data; name=“overwrite”
                    * name: 常に“overwrite”(必須)
                * body
                    * "true"を指定した場合、サーバの同ディレクトリに同盟ファイルが存在した場合に上書きする。
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            ```
            {
              "fileNames":[
                "path/to/filename1.parquet",
                "path/to/filename2.parquet"
              ]
            }
            ```
    * 異常(不正パラメータ)
        * 条件
            * ディレクトリ名/ファイル名が不正の場合
        * ステータスコード: 400
        * ボディ: ```{"errorMessage":"Invalid destination dir:{destDir}"}```
    * 異常(アップロードファイルなし)
        * 条件
            * アップロード対象が未指定の場合
        * ステータスコード: 400
        * ボディ: ```{"errorMessage": "No files to upload."}```
    * 異常(不正パラメータ)
        * 条件
            * リクエストがMultipartではなかった場合
        * ステータスコード: 400
        * ボディ: ```{"errorMessage": "request is not multipart."}```
    * 異常(ファイル存在エラー)
        * 条件
            * 同一ファイルが存在した場合
        * ステータスコード: 400
        * ボディ: ```{"errorMessage": "Target file exists. file:xxxxx"}```
    * 異常(ファイル保存エラー)
        * 条件
            * 何らかの理由でファイル書き込みに失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```
* Note:
  * 指定されたディレクトリが存在しない場合は、作成してからファイルを保存する。
  * ストレージ領域のルートディレクトリをSTORAGE_DIRとした場合、[STORAGE_DIR]/[uid]/[destDir]/[file_name]にファイルを保存する。

## ファイルダウンロードAPI

* 概要: パスを指定し、1ファイルをダウンロードする。
* リクエスト
    * メソッド:GET
    * パス: /api/download/{path_to_file}
    * パラメータ:
        * path_to_file(PATHパラメータ): ダウンロードするファイルパス。STORAGE_DIR/[uid]からの相対パス。
           * パス表現はURLエンコーディングが必須。（「/」は%2Fで表現する）
        * csv(クエリパラメータ): trueを指定し、ダウンロード対象のファイルの拡張子が「.parquet」の場合、parquetからCSVに変換してダウンロードする。
            * 変換後のCSVのファイル名は「<変換元ファイルのベース名>_yyyyMMddHHmmssSSS.csv」とする。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/octet-stream もしくは ファイルに合わせたMIME Type。
        * Content-Disposition: attachment; filename="<対象のファイル名>"
        * ボディ: MIMEエンコードしたファイルコンテンツ
    * 異常(該当ファイルなし)
        * 条件
            * 指定したファイルが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "File not found. path:{path_to_file}"}```
    * 異常(ディレクトリ指定不正)
        * 条件
            * 指定したディレクトリ指定が不正な場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ:```{"errorMessage": "Invalid file path. path:{path_to_file}"}```
    * 異常(ファイル読み込みエラー)
        * 条件
            * 何らかの理由でファイル読み込みに失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```

## ファイル一括ダウンロードAPI

* 概要: 複数のパスを指定し、Zipファイルの形式でダウンロードする。
    * １つのディレクトリに指定したパスのリストに対応するファイル群をフラットに配置したZipを返す。
    * Zipのファイル名は「belayer_download_yyyyMMddHHmmssSSS.zip」とする。yyyyMMddHHmmssSSSはサーバが処理した時点のUTC時刻（年月日時分秒+ミリ秒)
* リクエスト
    * メソッド:POST
    * パス: /api/downloadzip
    * パラメータ:
        * csv(クエリパラメータ): trueを指定し、ダウンロード対象のファイルの拡張子が「.parquet」の場合、parquetからCSVに変換したファイルをZipに格納し、ダウンロードする。
            * 変換後のCSVのファイル名は「<変換元ファイルのベース名>_yyyyMMddHHmmssSSS.csv」とする。
    * ボディ:
        * pathList: ダウンロード対象のパスのリスト

            ```
            {
               pathList: [
                 "dir1/FOO_TBL0.parquet",
                 "dir1/FOO_TBL1.parquet",
               ]
            }
            ```
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Disposition: attachment; filename="<belayer_download_yyyyMMddHHmmssSSS>.zip"
        * Content-Type: application/zip
        * ボディ: MIMEエンコードしたファイルコンテンツ
        * ボディ: 対象ファイル群をZipに格納したファイルコンテンツ
    * 異常(該当ファイルなし)
        * 条件
            * 指定したファイルが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "File not found. path:{path_to_file}"}```
    * 異常(ディレクトリ指定不正)
        * 条件
            * 指定したディレクトリ指定が不正な場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ:```{"errorMessage": "Invalid file path. path:{path_to_file}"}```
    * 異常(ファイル読み込みエラー)
        * 条件
            * 何らかの理由でファイル読み込みに失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```

## ファイル削除API

* 概要: パスを指定し、1ファイルを削除する。
* リクエスト
    * メソッド:POST
    * パス: /api/delete/file
    * パラメータ:なし
    * Content-Type: application/json
    * ボディ:
        * path: 削除するファイルパス。STORAGE_DIR/[uid]からの相対パス。
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            ```
            {"path": "path/to/filename1.parquet"}
            ```
    * 異常(該当ファイルなし)
        * 条件
            * 指定したファイルが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "File not found. path:{path_to_file}"}```
    * 異常(不正パス)
        * 条件
            * 指定したファイルがユーザ階層以外のパスの場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "Invalid file path. path:{path_to_file}"}```
    * 異常(ファイル削除エラー)
        * 条件
            * 何らかの理由でファイル削除に失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```

## ファイル複数削除API

* 概要: パスを複数指定し、ファイルを削除する。
* リクエスト
    * メソッド:POST
    * パス: /api/delete/files
    * パラメータ:なし
    * Content-Type: application/json
    * ボディ:
        * pathList: 削除対象のパスのリスト

            ```
            {
               pathList: [
                 "dir1/FOO_TBL0.parquet",
                 "dir1/FOO_TBL1.parquet",
               ]
            }
            ```
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            ```
            {
               pathList: [
                 "dir1/FOO_TBL0.parquet",
                 "dir1/FOO_TBL1.parquet",
               ]
            }
            ```
    * 異常(該当ファイルなし)
        * 条件
            * 指定したファイルが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "File not found. path:{path_to_file}"}```
    * 異常(不正パス)
        * 条件
            * 指定したファイルがユーザ階層以外のパスの場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "Invalid file path. path:{path_to_file}"}```
    * 異常(ファイル削除エラー)
        * 条件
            * 何らかの理由でファイル削除に失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```


## ディレクトリ削除API

* 概要: パスを指定し、1ディレクトリを削除する。
* リクエスト
    * メソッド:POST
    * パス: /api/delete/dir
    * パラメータ:なし
    * Content-Type: application/json
    * ボディ:
        * path: 削除するディレクトリパス。STORAGE_DIR/[uid]からの相対パス。(必須)
        * force: trueの場合、ディレクトリ内のファイルごと削除する。デフォルトはfalse。(任意)
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            ```
            {"path": "path/to/dir"}
            ```
    * 異常(該当ディレクトリなし)
        * 条件
            * 指定したディレクトリが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "Directory not found. path:{path_to_dir}"}```
    * 異常(ファイルが存在する)
        * 条件
            * 指定したディレクトリ内にファイルが１件以上存在する場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "One or more files found in the directory. path:{path_to_dir}"}```
    * 異常(不正パス)
        * 条件
            * 指定したディレクトリがユーザ階層以外のパスの場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "Invalid directory path. path:{path_to_file}"}```
    * 異常(ディレクトリ削除エラー)
        * 条件
            * 何らかの理由でディレクトリ削除に失敗した場合
        * ステータスコード: 500
        * ボディ: ```{"errorMessage": "Unexpected Error occurred."}```

## ディレクトリ一覧取得API

* 概要: サーバ上のディレクトリ名を指定し、配下のディレクトリ/ファイル群を表示する。
* リクエスト
    * メソッド:GET
    * パス: /api/dirlist/{dir_path}
    * パラメータ:
        * dir_path(PATHパラメータ): サーバ上のディレクトリ名。STORAGE_DIR/[uid]からの相対パス。
            * パス表現はURLエンコーディングが必須。（「/」は%2Fで表現する）
            * ルートディレクトリを指定する場合は、"."を指定する。
        * `hide_dir` か `hide_file` のいずれかを指定可能。
            * hide_dir(クエリパラメータ): trueを指定した場合、ディレクトリは含まず、ファイル一覧のみ返却する。
            * hide_file(クエリパラメータ): trueを指定した場合、ファイルは含まず、ディレクトリ一覧のみ返却する。
            * どちらも指定しない場合は、ディレクトリとファイルの一覧を返却する。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * filepath:ファイルパスの配列。STORAGE_DIR/[uid]/[dir_path]配下のファイルをすべて返す。
                * ディレクトリは末尾に"/"を付加して表現する。
            * message:ディレクトリ、ファイル数が上限（デフォルト値:500）を超過した場合のメッセージ。正常時はnull。

                ```
                {
                    "fileNames": [
                        "dp1/",
                        "dp1/8DeaZgjg/",
                        "dp1/8DeaZgjg/dumpFile1.parquet",
                        "dp1/8DeaZgjg/dumpFile2.parquet",
                        "dp1/8DeaZgjg/dumpFile3.parquet",
                        "dp1/xUTandJD/",
                        "dp1/xUTandJD/dumpFile1.parquet",
                        "dp1/xUTandJD/dumpFile2.parquet",
                        "dp1/xUTandJD/dumpFile3.parquet"
                    ],
                    "message": "List size is over limit. size=xxxx"
                }
                ```
    * 異常(該当ディレクトリなし)
        * 条件
            * 指定したディレクトリが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "Directory Not Found"}```

## バックアップ実行指示API

* 概要: バックアップファイルを保存するディレクトリを指定し、バックアップ取得を指示する。
    * バックアップ指示が成功した段階でレスポンスを返却する。（バックアップ処理完了ではない。）
* リクエスト
    * メソッド:POST
    * パス: /api/backup
    * パラメータ:なし
    * Content-Type：application/json
    * ボディ
        * dirPath: バックアップファイル(Zip)を格納するディレクトリパス。
            * STORAGE_DIR/[uid]/[dirPath]/[jobId]にバックアップファイルが保存される。
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * jobId: バックアップ処理の状態を取得する際に使用するジョブID
            * uid: バックアップ処理を実行したユーザID
            * type: "backup"
            ```
            {
                "jobId": "[JOB_ID]",
                "uid": "[UID]",
                "type": "backup"
            }
            ```
    * 異常(不正パス)
        * 条件
            * バックアップファイルを保存するディレクトリのパスが不正の場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ: ```{"errorMessage": "<エラー メッセージ>"}```
* Note:
    * API内部でTsurugi DBのオフライン状態をチェックし、バックアップ方法を自動選択する。
        * Tsurugi DBがオンラインの場合は、Tsubakuro経由でバックアップ実行する。
        * Tsurugi DBがオフラインの場合は Tsurugi CLIを実行してバックアップを実行する。


## データリストア実行指示API

* 概要: バックアップファイルを格納しているディレクトリを指定し、リストア実行を指示する。
    * リストア指示が成功した段階でレスポンスを返却する。（リストア処理完了ではない。）
* リクエスト
    * メソッド:POST
    * パス: /api/restore
    * パラメータ:なし
    * Content-Type: application/json
    * ボディ:
        * zipFilePath: バックアップファイル(Zip)の格納パス。
            * STORAGE_DIR/[uid]/[zip_file_path]にバックアップファイルが保存されている前提。
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * jobId: リストア処理の状態を取得する際に使用するジョブID
            * uid: リストア処理を実行したユーザID
            * type: "restore"
            ```
            {
                "jobId": "[jobId]",
                "uid": "[uid]",
                "type": "restore"
            }
            ```
    * 異常（該当ファイルなし）
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:
            ```{"errorMessage": "Invalid path. path:<ファイルパス>"}```
* Note:
    * リストアの稼働状況はバックアップ／リストア実行ステータス取得APIで確認する。

## バックアップ／リストア一覧取得API

* 概要: 自分が指示したバックアップ／リストアの実行状況を一覧で取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/br/list/{type}
    * パラメータ:
        * type(PATHパラメータ): "backup","restore"のいずれか
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:バックアップの場合
            * type: "backup"
            * jobId: バックアップを識別するジョブID
            * uid: バックアップ処理を実行したユーザID
            * status: "RUNNING","COMPLETED","FAILED","CANCELED"のいずれか
            * progress: ジョブ進捗率%(0～100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                jobList: [
                    {
                        "type": "backup",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "FAILED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": "[errorMessage]"
                        "output": "[output]",
                        "destDir": "[destDir]",
                        "zipFilePath": null
                    },
                    {
                        "type": "backup",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "CANCELED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": "[errorMessage]"
                        "output": "[output]",
                        "destDir": "[destDir]",
                        "zipFilePath": null
                    },
                    {
                        "type": "backup",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "COMPLETED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": null,
                        "output": "[yyyy-MM-dd'T'HH:mm:ss]finish:success",
                        "destDir": "dump1",
                        "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
                    },
                    {
                        "type": "backup",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "RUNNING",
                        "progress": 50,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": null,
                        "errorMessage": null,
                        "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NN.NN%",
                        "destDir": "dump1",
                        "zipFilePath": null
                    },
                ]
            }
            ```
        * ボディ:リストアの場合
            * type: "restore"
            * jobId: リストアを識別するジョブID
            * uid: リストア処理を実行したユーザID
            * status: "RUNNING","COMPLETED","FAILED","CANCELED"のいずれか
            * progress: ジョブ進捗率%(0～100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                jobList: [
                    {
                        "type": "restore",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "FAILED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": "[errorMessage]"
                        "output": "[output]",
                        "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
                    },
                    {
                        "type": "restore",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "CANCELED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": "[errorMessage]"
                        "output": "[output]",
                        "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
                    },
                    {
                        "type": "restore",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "COMPLETED",
                        "progress": 100,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "errorMessage": null,
                        "output": "[yyyy-MM-dd'T'HH:mm:ss]finish:success",
                        "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
                    },
                    {
                        "type": "restore",
                        "jobId": "[jobId]",
                        "uid": "[uid]",
                        "status": "RUNNING",
                        "progress": 50,
                        "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                        "endTime": null,
                        "errorMessage": null,
                        "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NN.NN%",
                        "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
                    },
                ]
            }
            ```
    * 異常(type不正)
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```

## バックアップ／リストア実行ステータス取得API

* 概要: バックアップ／リストアの実行ステータスを取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/br/status/{type}/{jobId}
    * パラメータ:
        * type(PATHパラメータ): "backup","restore"のいずれか
        * jobId(PATHパラメータ): バックアップ/リストアを識別するジョブID。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: typeにbackupを指定、実行中の場合
            * type: "backup"
            * jobId: バックアップを識別するジョブID
            * uid: バックアップ処理を実行したユーザID
            * status: "RUNNING"
            * progress: ジョブ進捗率%(0～100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "RUNNING",
                "progress": 50,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": null,
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NN.NN%",
                "destDir": "dump1",
                "zipFilePath": null
            }
            ```
        * ボディ: typeにbackupを指定、実行完了の場合
            * type: "backup"
            * jobId: バックアップを識別するジョブID
            * uid: バックアップ処理を実行したユーザID
            * status: "COMPLETED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "COMPLETED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]finish:success",
                "destDir": "dump1",
                "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
            }
            ```
        * ボディ: typeにbackupを指定、バックアップ失敗の場合
            * type: "backup"
            * jobId: バックアップを識別するジョブID
            * uid: バックアップ処理を実行したユーザID
            * status: "FAILED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "FAILED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": "[errorMessage]"
                "output": "[output]",
                "destDir": "[destDir]",
                "zipFilePath": null
            }
            ```
        * ボディ: typeにbackupを指定、キャンセル済みの場合
            * type: "backup"
            * jobId: バックアップを識別するジョブID
            * uid: バックアップ処理を実行したユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "CANCELED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": "[errorMessage]"
                "output": "[output]",
                "destDir": "[destDir]",
                "zipFilePath": null
            }
            ```
        * ボディ: typeにrestoreを指定、実行中の場合
            * type: "restore"
            * jobId: リストアを識別するジョブID
            * uid: リストア処理を実行したユーザID
            * status: "RUNNING"
            * progress: ジョブ進捗率%(0～100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "restore",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "RUNNING",
                "progress": 50,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": null,
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NN.NN%",
                "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
            }
            ```
        * ボディ: typeにrestoreを指定、実行完了の場合
            * type: "restore"
            * jobId: リストアを識別するジョブID
            * uid: リストア処理を実行したユーザID
            * status: "COMPLETED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "restore",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "COMPLETED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]finish:success",
                "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
            }
            ```
        * ボディ: typeにrestoreを指定、バックアップ失敗の場合
            * type: "restore"
            * jobId: リストアを識別するジョブID
            * uid: リストア処理を実行したユーザID
            * status: "FAILED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "restore",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "FAILED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": "[errorMessage]"
                "output": "[output]",
                "zipFilePath": "dump1/backup-bfBMv5Ba.zip"
            }
            ```
        * ボディ: typeにbackupを指定、キャンセル済みの場合
            * type: "restore"
            * jobId: リストアを識別するジョブID
            * uid: リストア処理を実行したユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "CANCELED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": "[errorMessage]"
                "output": "[output]",
                "zipFilePath": null
            }
            ```
    * 異常(該当ジョブなし)
        * 条件
            * 指定したジョブが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```
    * 異常(type不正)
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```

## バックアップ／リストアキャンセルAPI

* 概要: バックアップ／リストアの実行を中止する。
* リクエスト
    * メソッド:POST
    * パス: /api/br/cancel/{type}/{jobId}
    * パラメータ:
        * type(PATHパラメータ): "backup","restore"のいずれか
        * jobId(PATHパラメータ): バックアップ/リストアを識別するジョブID。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: typeにbackupを指定、ジョブが実行中の場合
            * type: "backup"
            * jobId: 指定したジョブID
            * uid: キャンセルを実行したユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * destDir: 指定したディレクトリパス
            * zipFilePath: 格納したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "backup",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "CANCELED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NNN.NN%",
                "destDir": "dir1",
                "zipFilePath": null
            }
            ```
        * ボディ: typeにrestoreを指定、ジョブが実行中の場合
            * type: "restore"
            * jobId: 指定したジョブID
            * uid: キャンセルを実行したユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * output: 進捗状況
            * zipFilePath: 指定したバックアップファイルのパス(Zip形式)
            ```
            {
                "type": "restore",
                "jobId": "[jobId]",
                "uid": "[uid]",
                "status": "CANCELED",
                "progress": 100,
                "startTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "endTime": "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00",
                "errorMessage": null,
                "output": "[yyyy-MM-dd'T'HH:mm:ss]progress:NNN.NN%",
                "zipFilePath": "dir1/backup-BlsTCJVJ.zip"
            }
            ```
    * 異常(該当ジョブなし)
        * 条件
            * 指定したジョブが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ:
            ```{"errorMessage": "Specified job is not found. jobId:<ジョブID>"}```
    * 異常(ステータス不正)
        * 条件
            * 指定したジョブが実行中以外の場合
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ:
            ```{"errorMessage":"Can't cancel job. status=<ステータス>"}```
    * 異常(type不正)
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```

## ダンプ取得API

* 概要: ダンプファイルを保存するディレクトリを指定し、オフラインでダンプ取得を指示する。
* リクエスト
    * メソッド:POST
    * パス: /api/dump/{table_name}
    * Content-Type: application/json
    * パラメータ:
        * table_name(PATHパラメータ): ターゲットのテーブル名。
    * ボディ:
        * dirPath: ダンプファイルを格納するディレクトリパス。(必須)
            * STORAGE_DIR/[uid]/[dirPath]/[jobId]にダンプファイルが保存される。
        * format: "csv"をパラメータ値を指定した場合、CSV形式のフォーマットでダンプファイルを取得する。(任意)
            * パラメータ省略時もしくは"csv"以外の場合は、はParquet形式のフォーマットでダンプファイルを取得する。
        * waitUntilDone: trueを値に指定した場合、完了（正常or異常）までレスポンスを返さない。デフォルトはfalse。(任意)
* レスポンス
    * 正常(wait_until_done指定なしの場合)
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: ```{"jobId": "[jobId]"}```
            * jobId: ダンプ処理の状態を取得する際に使用するジョブID
    * 正常(wait_until_done指定ありの場合)
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ:
            * jobId: ダンプ／ロードを識別するジョブID
            * type: "dump","load"のいずれか
            * dirPath: ダンプ／ロード時に指定したディレクトリパス
            * table: テーブル名(複数)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * status: "COMPLETED","FAILED","CANCELED"のいずれか
            * progress: ジョブ進捗率%(0～100)
            * files: 生成したファイルのダウンロードパス

            ```
            {
              "jobId": "[jobId]",
              "type": "dump",
              "dirPath": "dir1",
              "table": "FOO_TBL",
              "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
              "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
              "status": "COMPLETED",
              "progress": 100,
              "files": [
                 "dir1/FOO_TBL0.parquet",
                 "dir1/FOO_TBL1.parquet",
              ]
            }
                ```
            ```
            ```

    * 異常(不正パラメータ)
        * 条件
            * 不正なディレクトリ指定
            * 存在しないテーブル名の指定
            * パラメータのフォーマット不正
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラーメッセージ>"}```
    * 異常(オフライン時)
        * 条件
            * DBがオフラインの場合
        * ステータスコード: 406
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラーメッセージ>"}```
    * 異常(ダンプ指示失敗)
        * 条件
            * 何らかの理由でダンプ指示が失敗した場合
        * ステータスコード: 500
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラーメッセージ>"}```
* Note:
    * ダンプ指示が成功した段階でレスポンスを返却する。
        * wait_until_doneパラメータを指定した場合、ダンプが完了するまでレスポンスを返さない。
            * **<u>補足: 自動ダウンロードを実行するために、完了を待つスイッチを用意している。</u>**

## データロードAPI

* 概要: サーバ上のに格納しているデータファイルを指定し、ロード実行を指示する。ロード対象と同一のプリマリーキーのデータが存在する場合は、当該レーコードはロードデータで上書きされる。ロード先テーブルの既存データは前述の上書きされるケースを除き維持される。
* リクエスト
    * メソッド:POST
    * パス: /api/load/{table_name}
    * パラメータ:
        * table_name(PATHパラメータ): ロード先のテーブル名
    * Content-Type: application/json
    * ボディ:
        * files: サーバに配置したデータファイルのパス（複数、1つ以上必須）
        * format: ロードするデータファイルのフォーマットを指定する。(任意)
            * "parquet"/"csv"/"zip"/"detect_by_ext"のいずれか。
            * パラメータ省略時は、csv/parquet/zipを拡張子によって自動判別する`detect_by_ext`とみなす。判別ができない場合は、Parquet形式とみなして処理する。
            * zipの場合、zip内のファイルの拡張子によってparquet/csvを判別してロード処理を行う。
        * transactional: trueの場合もしくは未指定の場合、１トランザクション内でロードする。デフォルトはtrue。(任意)
            * falseを指定した場合はトランザクションを分割して高速ロードする。
        * waitUntilDone: trueを値に指定した場合、完了（正常or異常）までレスポンスを返さない。デフォルトはfalse。(任意)
        * mappings: カラムマッピング(任意)
            * targetColumn: ロード先テーブルのカラム名、もしくは「@N」形式のカラム番号(Nはカラム番号の数値）
            * sourceColumn: ロード元データファイル上のカラム名、もしくは「@N」形式のカラム番号(Nはカラム番号の数値）

        ```
        {
           files: [
             "dir1/FOO_TBL0.parquet",
             "dir1/FOO_TBL1.parquet",
           ],
           format: "csv",
           transactional: true,
           waitUntilDone: true,
           mappings: [
             {
               targetColumn: "tabel_col1",
               sourceColumn: "parquet_col1",
             },
             {
               targetColumn: "tabel_col2",
               sourceColumn: "parquet_col2",
             },
           ]
        }
        ```

* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: ```{"jobId": "[jobId]"}```
            * jobId: ロード処理の状態を取得する際に使用するジョブID
    * 異常(オフライン時)
        * 条件
            * DBがオフラインの場合
        * ステータスコード: 406
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```
    * 異常(該当ファイルなし)
        * 条件
            * データファイルパスが誤っている
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ:```{"errorMessage": "Invalid path. path:<ファイルパス>"}```
    * 異常(パラメータ不正)
        * 条件
            * データファイルパスが指定されていない
            * カラムマッピングのフォーマット不正
        * ステータスコード: 400
        * Content-Type: application/json
        * ボディ:
            * ```{"errorMessage":"No dump file is specified."}```
            * ```{"errorMessage":"Invalid mapping definition. [target|source] column:<value>"}```
    * 異常(ロード指示失敗)
        * 条件
            * 何らかの理由でロード指示が失敗した場合
        * ステータスコード: 500
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```
* Note:
    * ロード指示が成功した段階でレスポンスを返却する。ロードの稼働状況はダンプ／ロード実行ステータス取得APIで確認する。
        * wait_until_doneパラメータを指定した場合、ロードが完了するまでレスポンスを返さない。
            * **<u>補足: ロード完了のイベントを受け取るため、完了を待つスイッチを用意している。</u>**

## ダンプ／ロード一覧取得API

* 概要: 自分が指示したダンプ／ロードの実行状況を一覧で取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/dumpload/list/{type}
    * パラメータ:
        * type(PATHパラメータ): "dump","load"のいずれか
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 実行中の場合
            * jobId: ダンプ／ロードを識別するジョブID
            * type: "dump","load"のいずれか
            * dirPath: ダンプ／ロード時に指定したディレクトリパス
            * table: テーブル名
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * status: "RUNNING","COMPLETED","FAILED","CANCELED"のいずれか
            * progress: ジョブ進捗率%(0～100)

                ```
                {
                  jobList: [
                    {
                      "jobId": "[jobId]",
                      "type": "dump",
                      "dirPath": "dir1",
                      "table": "FOO_TBL",
                      "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                      "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                      "status": "CANCELED",
                      "progress": 100,
                    },
                    {
                      "jobId": "[jobId]",
                      "type": "dump",
                      "dirPath": "dir1",
                      "table": "FOO_TBL",
                      "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                      "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                      "status": "COMPLETED",
                      "progress": 100,
                    },
                    {
                      "jobId": "[jobId]",
                      "type": "dump",
                      "dirPath": "dir1",
                      "table": "FOO_TBL",
                      "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                      "endTime": null,
                      "status": "RUNNING",
                      "progress": 50,
                    },
                  ]
                }
                ```


## ダンプ／ロード実行ステータス取得API

* 概要: ダンプ／ロードの実行ステータスを取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/dumpload/status/[type]/{jobId}
    * パラメータ:
        * type(PATHパラメータ): "dump","load"のいずれか
        * jobId(PATHパラメータ): ダンプ/ロードを識別するジョブID。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 実行中の場合
            * type: "dump","load"のいずれか
            * jobId: 指定したジョブID
            * uid: ユーザID
            * status: "RUNNING"
            * progress: ジョブ進捗率%(0～100)
            * startTime: 実行指示日時
            * endTime: null
            * errorMessage: null
            * table: テーブル名
            * dirPath: 指定したディレクトリパス(type=dumpの場合のみ)
            * format: "parquet","csv", "detect_by_ext"のいずれか
                * ※"detect_by_ext"はフォーマットが未指定の場合
            * files: ダンプ/ロードしたファイル名(正常完了以外は空)

                ```
                {
                  "type": "dump",
                  "jobId": "[jobId]",
                  "uid": "[uid]",
                  "status": "RUNNING",
                  "progress": 50,
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": null,
                  "errorMessage": null,
                  "table": "FOO_TBL",
                  "dirPath": "dump1",
                  "format": "parquet",
                  "files": []
                }
                ```

        * ボディ: 実行完了の場合
            * type: "dump","load"のいずれか
            * jobId: 指定したジョブID
            * uid: ユーザID
            * status: "COMPLETED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: null
            * table: テーブル名
            * dirPath: 指定したディレクトリパス(type=dumpの場合のみ)
            * format: "parquet","csv","detect_by_ext"のいずれか
                * ※"detect_by_ext"はフォーマットが未指定の場合
            * files: ダンプ/ロードしたファイル名(複数)

                ```
                {
                  "type": "dump",
                  "jobId": "[jobId]",
                  "uid": "[uid]",
                  "status": "COMPLETED",
                  "progress": 100,
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "errorMessage": null,
                  "table": "FOO_TBL",
                  "dirPath": "dump1",
                  "format": "parquet",
                  "files": [
                      "dir1/FOO_TBL0.parquet",
                      "dir1/FOO_TBL1.parquet",
                  ]
                }
                ```

        * ボディ: ダンプ／ロード失敗の場合
            * type: "dump","load"のいずれか
            * jobId: 指定したジョブID
            * uid: ユーザID
            * status: "FAILED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: エラーメッセージ
            * table: テーブル名
            * dirPath: 指定したディレクトリパス(type=dumpの場合のみ)
            * format: "parquet","csv","detect_by_ext"のいずれか
                * ※"detect_by_ext"はフォーマットが未指定の場合
            * files: ダンプ/ロードしたファイル名(正常完了以外は空)

                ```
                {
                  "type": "dump",
                  "jobId": "[jobId]",
                  "uid": "[uid]",
                  "status": "FAILED",
                  "progress": 100,
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "errorMessage": "[message]",
                  "table": "FOO_TBL",
                  "dirPath": "dir1",
                  "format": "parquet",
                  "files": []
                }
                ```
        * ボディ: キャンセル済みの場合
            * type: "dump","load"のいずれか
            * jobId: 指定したジョブID
            * uid: ユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: null
            * table: テーブル名
            * dirPath: 指定したディレクトリパス(type=dumpの場合のみ)
            * format: "parquet","csv","detect_by_ext"のいずれか
                * ※"detect_by_ext"はフォーマットが未指定の場合
            * files: ダンプ/ロードしたファイル名(正常完了以外は空)

                ```
                {
                  "type": "dump",
                  "jobId": "[jobId]",
                  "uid": "[uid]",
                  "status": "CANCELED",
                  "progress": 100,
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "errorMessage": null,
                  "table": "FOO_TBL",
                  "dirPath": "dump1",
                  "format": "parquet",
                  "files": []
                }
                ```

    * 異常(該当ジョブなし)
        * 条件
            * 指定したジョブが存在しない場合
        * ステータスコード: 404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```


## ダンプ／ロードキャンセルAPI

* 概要: ダンプ／ロードの実行を中止する。
* リクエスト
    * メソッド:POST
    * パス: /api/dumpload/cancel/{type}/{jobId}
    * パラメータ:
        * type(PATHパラメータ): "dump","load"のいずれか
        * jobId(PATHパラメータ): ダンプ/ロードを識別するジョブID。
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ
            * type: "dump","load"のいずれか
            * jobId: 指定したジョブID
            * uid: ユーザID
            * status: "CANCELED"
            * progress: ジョブ進捗率%(100)
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * errorMessage: null
            * table: テーブル名
            * dirPath: 指定したディレクトリパス(type=dumpの場合のみ)
            * format: "parquet","csv","detect_by_ext"のいずれか
                * ※"detect_by_ext"はフォーマットが未指定の場合
            * files: ダンプ/ロードしたファイル名(正常完了以外は空)

                ```
                {
                  "type": "dump",
                  "jobId": "[jobId]",
                  "uid": "[uid]",
                  "status": "CANCELED",
                  "progress": 100,
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "errorMessage": null,
                  "table": "FOO_TBL",
                  "dirPath": "dump1",
                  "format": "parquet",
                  "files": []
                }
                ```

    * 異常(該当なし)
        * 条件
            * トランザクションIDに該当するものが存在しない。
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```
    * 異常(ステータス異常)
        * 条件
            * 既に正常終了、失敗、キャンセル済みの場合。
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```

## トランザクション開始API

* 概要: トランザクションを開始する。
    * Note: トランザクションを開始して待機する期間中は、内部処理にて定期的にタイムアウトを延長する。
* リクエスト
    * メソッド:POST
    * パス: /api/transaction/begin
    * パラメータ:なし
    * Content-Type: application/json
    * ボディ:
        * type: "read_write","read_only"のいずれか(必須)
        * timeoutMin: トランザクションタイムアウト値（分）(必須)
        * tables: トランザクション内で書き込みを行うテーブル名の配列(typeがread_writeの場合は必須)
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 実行中の場合
            * transactionId: トランザクションを識別するトランザクションID
            * type: "read_write","read_only"のいずれか
            * startTime: 実行指示日時
            * endTime: 常にnull
            * status: "AVAILABLE"
            * progress: ジョブ進捗率%(0)

                ```
                {
                  "transactionId": "RRMkrjvK",
                  "type": "read_write",
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": null,
                  "status": "AVAILABLE"
                  "progress": 0,
                }
                ```

## トランザクションコミット/ロールバックAPI

* 概要: トランザクションをコミット／ロールバックする。
    * トランザクションのステータスが`AVAILABLE`の場合のみコミット可能
    * トランザクションのステータスが`AVAILABLE`もしくは`IN_USE`の場合のみロールバック可能
* リクエスト
    * メソッド:POST
    * パス: /api/transaction/{type}/{transactionId}
    * パラメータ:
        * type(PATHパラメータ): "commit","rollback"のいずれか
        * transactionId(PATHパラメータ): トランザクションを識別するトランザクションID
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * transactionId: トランザクションを識別するトランザクションID
            * type: "read_write","read_only"のいずれか
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * status: "COMMITTED","ROLLBACK_COMPLETED"
            * progress: ジョブ進捗率%(0)

                ```
                {
                  "transactionId": "RRMkrjvK",
                  "type": "read_write",
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "status": "COMMITTED"
                  "progress": 0,
                }
                ```

    * 異常（該当なし）
        * 条件
            * トランザクションIDに該当するものが存在しない。
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"<エラー メッセージ>"}```
    * 異常（トランザクションのステータスがコミット/ロールバック不可）
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ: コミット/ロールバック失敗の場合

            ```
            {"errorMessage":"<エラー メッセージ>"}
            ```

    * 異常（コミット/ロールバック失敗）
        * ステータスコード:500
        * Content-Type: application/json
        * ボディ: コミット/ロールバック失敗の場合

            ```
            {"errorMessage":"<エラー メッセージ>"}
            ```

## トランザクション確認API

* 概要: トランザクションの状態を返却する。
* リクエスト
    * メソッド:GET
    * パス: /api/transaction/status/{transactionId}
    * パラメータ:
        * transactionId(PATHパラメータ): トランザクションを識別するトランザクションID
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * transactionId: トランザクションを識別するトランザクションID
            * type: "read_write","read_only"のいずれか
            * startTime: 実行指示日時
            * endTime: 処理完了日時
            * status:
                * AVAILABLE: トランザクションが有効な状態
                * IN_USE: ダンプもしくはロード中の状態
                * COMMITTED: コミットされてトランザクションが終了した状態
                * ROLLBACK_COMPLETED: ロールバックされてトランザクションが終了した状態
                * FAILED: 処理失敗によりトランザクションが終了した状態

                ```
                {
                  "transactionId": "RRMkrjvK",
                  "type": "read_write",
                  "startTime": "YYYY-MM-DD HH:MM:SS.SSS+00:00",
                  "endTime": null,
                  "status": "AVAILABLE"
                  "progress": 0,
                }
                ```
            * progress: ジョブ進捗率%(0)

    * 異常（該当なし）
        * 条件
            * トランザクションIDに該当するものが存在しない。
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"Specified transaction is not found. transactionId:{transactionId}"}```

## ストリームデータダンプAPI

* 概要: 指定したトランザクション内でテーブルのダンプファイルを取得する。
    * トランザクションのステータスが`AVAILABLE`もしくは`IN_USE`の場合のみ実行可能
    * STORAGE_DIR/[uid]/[transaction_id]にダンプファイルを書き出し、レスポンスでダウンロードパスを返す。
* リクエスト
    * メソッド:POST
    * パス: /api/transaction/dump/{transactionId}/{table_name}
    * パラメータ:
        * transactionId(PATHパラメータ): トランザクションを識別するトランザクションID
        * table_name(PATHパラメータ): ダンプするテーブル名
    * Content-Type: application/json
    * ボディ:
        * format: "csv"をパラメータ値を指定した場合、CSV形式のフォーマットでダンプファイルを取得する。(任意)
            * パラメータ省略時もしくは"csv"以外の場合は、はParquet形式のフォーマットでダンプファイルを取得する。
        * mode: 以下のいずれか(任意)
            * normal(デフォルト): 生成したダンプファイルのダウンロードパス(複数)を処理完了時にまとめて受け取る。
            * sse: 生成したダンプファイルのダウンロードパスを1ファイル単位でServer-Sent Eventsで受け取る。
* レスポンス
    * 正常（mode=normalの場合）
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * transactionId: トランザクションを識別するトランザクションID
            * table: 指定したテーブル名
            * files: 生成したダンプファイルのダウンロードパスの配列
            * format: parquet もしくは csv

                ```
                {
                  "transactionId": "RRMkrjvK",
                  "table", "FOO_TBL",
                  "downloadPathList": [
                      "dir1/RRMkrjvK/FOO_TBL0.parquet",
                      "dir1/RRMkrjvK/FOO_TBL1.parquet",
                  ],
                  "format": "parquet"
                }
                ```

    * 正常（mode=sseの場合）
        * ステータスコード:200
        * Content-Type: text/event-stream
        * ボディ:
            * ダンプ完了時にdata:行を返す。終了時はレスポンスがクローズされる。

                ```
                data:table_name=FOO_TBL

                data:transactionid=IZb1gY3I

                data:format=parquet

                data:download_path=IZb1gY3I%2FdumpFile1.parquet

                data:download_path=IZb1gY3I%2FdumpFile2.parquet

                data:download_path=IZb1gY3I%2FdumpFile3.parquet
                ```

    * 異常（該当なし）
        * 条件
            * トランザクションIDに該当するものが存在しない。
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"Specified transaction is not found. transactionId:{transactionId}"}```

## ストリームデータロードAPI

* 概要: 指定したトランザクション内でテーブルにデータをロードする。
    * ロード対象と同一のプリマリーキーのデータが存在する場合は、当該レーコードはロードデータで上書きされる。ロード先テーブルの既存データは前述の上書きされるケースを除き維持される。
    * トランザクションのステータスが`AVAILABLE`もしくは`IN_USE`の場合のみ実行可能
    * STORAGE_DIR/[uid]/[transaction_id]にデータファイルをアップロードし、ロードする。
* リクエスト
    * メソッド:POST
    * パス: /api/transaction/load/{transactionId}/{table}
    * パラメータ:
        * transactionId(PATHパラメータ): トランザクションを識別するトランザクションID
        * table(PATHパラメータ): ロードするテーブル名
    * Content-Type: multipart/form-data
    * ボディ:
        * マルチパート形式（複数指定可能）
            * FilePart: アップロードするファイル(必須、複数指定可能)
                * header
                    * Content-Type: application/octet-stream
                    * Content-Disposition: form-data; name="file"; filename="path/to/real/FOO_TBL0.parquet"
                        * name: 常に"file"(必須)
                        * filename: 保存するファイルのローカルファイルパス(必須)
                * body
                    * ファイルコンテンツ(必須)
            * FormFieldPart:カラムマッピング(任意、複数指定可能)
                * header
                    * Content-Disposition: form-data; name="col-map"
                        * name: 常に"col-map"(必須)
                * body
                    * カンマ区切りのカラムマッピング(必須)
                        * FROM,TO
                        * 例1）データファイルのCOL_Aカラム値をテーブルのCOL_1カラムにロードする
                            * COL_A,COL_1
                        * 例2）データファイルのCOL_Aカラム値をテーブルの1番目のカラムにロードする
                            * COL_A,@1
            * FormFieldPart:フォーマット(任意)
                * header
                    * Content-Disposition: form-data; name="format"
                        * name: 常に"format"(必須)
                * body
                    * ロードするデータファイルのフォーマット。
                        * "parquet"/"csv"/"zip"/"detect_by_ext"のいずれか。
                        * FormFieldPart省略時は、csv/parquet/zipを拡張子によって自動判別する`detect_by_ext`とみなす。判別ができない場合は、Parquet形式とみなして処理する。
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * transactionId: トランザクションを識別するトランザクションID
            * table: 指定したテーブル名
            * dumpFiles: ロード完了したデータファイル名
            * format: parquet もしくは csv

                ```
                {
                  "transactionId": "RRMkrjvK",
                  "table": [
                      "FOO_TBL",
                  ],
                  "dumpFiles": [
                      "RRMkrjvK/FOO_TBL0.parquet",
                      "RRMkrjvK/FOO_TBL1.parquet",
                  ],
                  "format": "parquet"
                }
                ```

    * 異常（該当なし）
        * 条件
            * トランザクションIDに該当するものが存在しない。
        * ステータスコード:404
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"Specified transaction is not found. transactionId:{transactionId}"}```
    * 異常(不正パラメータ)
        * 条件
            * リクエストがMultipartではなかった場合
        * ステータスコード: 400
        * ボディ: ```{"errorMessage": "request is not multipart."}```
    * 異常（ファイル指定なし）
        * 条件
            * マルチパートでファイルコンテンツが指定されていない。
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"No dump file is specified."}```
    * 異常（カラムマッピング不正）
        * 条件
            * カラム指定のフォーマットが正しくない。
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":"Invalid mapping definition. [target|source] column:xxx}"}```

## セッションステータス確認API

* 概要: セッションの稼働状態を取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/session/status/{session_id}
    * パラメータ
        * session_id(PATHパラメータ): セッションID
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * status: 稼働状態
                * available: 正常に稼働している状態
                * unavailable: 稼働していない状態
            ```
            {"session_id": "<session_id>", "status": "available"}
            ```

## セッション変数設定API

* 概要: セッションに変数を設定する。
* リクエスト
    * メソッド:POST
    * パス: /api/session/set
    * パラメータ
        * session_id(PATHパラメータ): セッションID
    * Content-Type: application/json
    * ボディ:
        * session_id: セッションID
        * var_name: 変数名
        * var_value: 変数の値
        ```
        {"session_id": "<session_id>", "var_name":"<variable_name>", "var_value" : "<valieable_value>"}
        ```
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * sessionId: セッションID 
            * varName: セッション変数名
            ```
            {"session_id": "<session_id>", "var_name": "<variable_name>"}
            ```
    * 異常(パラメータ不正：セッションIDなし)
        * ステータスコード:400
        * ボディ: ```{"errorMessage": "invalid parameters."}```
    * 異常（セッション変数設定失敗）
        * 条件
            * 何らかの理由によりセッションに変数が設定できなかった場合。（セッションが利用不能の場合を含む）
        * ステータスコード:400
        * Content-Type: application/json
        * ボディ:```{"errorMessage":""unable to set variable to session :<sessionId>. (name:<variable_name>, value:<variable_value>)"}```

## セッション停止API

* 概要: 指定したセッションをkillする。
* リクエスト
    * メソッド:POST
    * パス: /api/session/kill
    * パラメータ: なし
    * Content-Type: application/json
    * ボディ:
        * session_id: セッションID
        ```
        {"session_id":"<session_id>"}
        ```
* レスポンス
    * 正常
        * ステータスコード:200
        * ボディ: なし
    * 異常(kill失敗)
        * ステータスコード:400
        * ボディ: ```{"errorMessage": "failed to kill session."}```
    * 異常(パラメータ不正：セッションIDなし)
        * ステータスコード:400
        * ボディ: ```{"errorMessage": "sessionId is not specified."}```

## DB起動API

* 概要: DBの起動を指示する。
    * DBの起動完了まで待機せずに終了する。1秒以内に起動コマンドのプロセスが異常終了しない場合は正常結果を返す。
    * timeout値はプロパティ`webapi.cli.timeout`値で変更可能
* リクエスト
    * メソッド:POST
    * パス: /api/db/start
    * パラメータ:なし
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * ボディ: なし

## DB停止API

* 概要: DBの停止を指示する。
    * DBの停止完了まで待機せずに終了する。1秒以内に停止コマンドのプロセスが異常終了しない場合は正常結果を返す。
    * timeout値はプロパティ`webapi.cli.timeout`値で変更可能
* リクエスト
    * メソッド:POST
    * パス: /api/db/shutdown
    * パラメータ:なし
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * ボディ: なし

## DBステータス確認API

* 概要: DBの稼働状態を取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/db/status
    * パラメータ:なし
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * status: 稼働状態
                * stop: 未稼働状態
                * starting: 起動し、稼働する手前の状態
                * running: 正常に稼働している状態
                * shutdown: シャットダウン処理を実行中
                * disconnected: 稼動していた痕跡はあるが、接続できない状態
                ```
                {"status": "running"}
                ```

## テーブル名一覧取得API

* 概要: DBに存在するアクセス可能なテーブルの一覧を取得する。
* リクエスト
    * メソッド:GET
    * パス: /api/db/tablenames
    * パラメータ:なし
    * ボディ:なし
* レスポンス
    * 正常
        * ステータスコード:200
        * Content-Type: application/json
        * ボディ: 処理成功の場合
            * tableNames: テーブル名のリスト

                ```
                {
                  "tableNames": [
                      "foo",
                      "bar",
                      "baz"
                  ]
                }
                ```

# ファイルフォーマット

## ダンプファイルCSVフォーマット

* 概要
    * CSV形式のダンプファイルのフォーマットを以下に定義する。
* 詳細
    * CSVのフォーマットは PostgreSQLのCOPYコマンドによるデフォルトのCSV出力仕様に従う。
        * PostgreSQLのCOPYで扱うCSVのデフォルトオプションと同様の仕様
            * 1行目は常にヘッダー行として扱う。
            * レコード区切りはLFである。
            * クォート文字はダブルクォーテーション(")である。
            * エスケープ文字はダブルクォーテーション(")である。
            * データ値に改行文字が入る場合は、データ値全体をクォート文字で囲う。
            * データ値にクォート文字を含む場合は、クォート文字の前にエスケープ文字を付加し、データ値全体をクォート文字で囲う。
        * PostgreSQLのCOPYで扱うCSVのデフォルトオプションと異なる仕様
            * 非NULL値を常にクォート文字列で囲む。(PostgreSQLのCOPY TOの「FORCE_QUOTE=*」の指定と同様)
                * 「クォート文字列で囲まれていない空文字」はNULLとして扱う。
                * 「クォート文字列で囲まれた空文字」は空文字(非NULL)として扱う。
        * CSVファイル読み込み時は、上記のクォート文字で囲う必要のないデータの場合、クォートの有無は問わない。

    * エンコーディングには常に`UTF-8`を使用する。
        * ファイル書き出し時：BOMなしUTF-8で出力する。
        * ファイル読み込み時：BOM有りの場合は、BOMを無視して読み込む。
    * 時刻はUTC時刻をマイクロ秒単位まで扱う。
        * 時刻は常にUTC時刻として扱う。
    * 各データ型の値の文字列表現は以下のとおり。

        |  SQL Type |  Tsurugi Type | Parquet Type | **CSV(Output)** | **CSV(Input)** |
        | --------- | ------------- | ------------ | ----------- | ---------- |
        |  BOOLEAN | boolean | Primitive Type: BOOLEAN | "true"もしくは"false" | Boolean.valueOf(String)<br>大文字小文字を判別せず、"true"はTrue。それ以外はFalse |
        |  TINYINT | int1 | Primitive Type: INT32<br>Logical Type: INT <br> bit width: 8 <br> signed: true | Short.toString() | Short.valueOf(String)<br>1バイトの範囲を超える場合はエラーとする。|
        |  SMALLINT| int2 | Primitive Type: INT32<br>Logical Type: INT <br> bit width: 16 <br> signed: true | 同上 | 同上 |
        |  INT | int4 | Primitive Type: INT32<br>Logical Type: INT <br> bit width: 32 <br> signed: true | Integer.toString() | Integer.valueOf(String) |
        |  BIGINT | int8 | Primitive Type: INT64<br>Logical Type: INT <br> bit width: 64 <br> signed: true | Long.toString() | Long.valueOf(String) |
        |  REAL | float4 | Primitive Type: FLOAT | Float.toString() | Float.valueOf(String) |
        |  DOUBLE | float8 | Primitive Type: DOUBLE | Double.toString() | Double.valueOf(String) |
        |  CHAR | character | Primitive Type: BYTE_ARRAY <br>Logical Type: STRING | 文字列をそのまま扱う | 文字列をそのまま扱う |
        |  VARCHAR | character varying | Primitive Type: BYTE_ARRAY <br>Logical Type: STRING | 同上 | 同上 |
        |  DECIMAL(p,s)| decimal | Primitive Type: BYTE_ARRAY <br>Logical Type: DECIMAL<br> precision: p <br> scale: s | BigDecimal.toPlainString()| BigDecimal.valueOf(String) |
        |  DATE| date | Primitive Type: INT32 <br>Logical Type: DATE | yyyy-MM-dd | 同左 |
        |  TIME| time_of_day | Primitive Type: INT64 <br>Logical Type: TIME <br> utc adjustment: true<br>unit: MICROS | UTCでの時刻 HH:mm:ss.SSSnnnnnn | 同左 |
        |  TIMESTAMP| time_point | Primitive Type: INT64 <br>Logical Type: TIMESTAMP <br> utc adjustment: true<br> unit: MICROS| UTCでの日時 yyyy-MM-dd HH:mm:ss.SSSnnnnnn | 同左 |

    * 例

        * 1行目はヘッダー行。ヘッダー行にはカラム名を記載する。
        * 2行目以降はデータ行
           * 1カラム目は文字列
           * 2カラム目は空文字列(非NULL)
           * 3カラム目はNULL
           * 3カラム目はTimestamp

        ```
        COL_A,COL_B,COL_C,COL_D
        "foobar","",,"2022-06-22 15:00:01.123456"
        ```

# Web API 補足事項

## ログ出力

* Belayer Web API Serverのログは標準出力に出力する。
* ログレベルは環境変数`BELAYER_LOG_LEVEL`で変更可能。ログレベルのデフォルト値は`WARN`。
* ログの設定については、spring-boot v2.6.6のログ設定方法に従う。
    * https://docs.spring.io/spring-boot/docs/2.6.6/reference/html/howto.html#howto.logging

## ジョブ情報の永続化

* バックアップ、リストア、ダンプ、ロードは非同期ジョブにて実行される。
* ジョブの記録は一定時間永続化される。
    * ジョブ登録日時から起算し、環境変数`BELAYER_JOB_EXPIRATION_DAYS`の日数だけ保持される。デフォルト値は`3`(3日間)。
* すべてのジョブを削除する際は以下の手順に従う。
    * Belayer Web API Serverを停止する。
    * 環境変数`BELAYER_STORAGE_ROOT`（未定義の場合は`/opt/belaer/storage`）にある`belayer_jobs.json`を削除する。
    * Belayer Web API Serverを起動する。

## テンポラリファイル

* Belayer WebAPIサーバではテンポラリディレクトリに一時ファイルを作成する。
    * テンポラリディレクトリ直下に`belayer-`で始まるディレクトリが作成され、そのディレクトリ内に一時ファイルが作成される。
    * テンポラリディレクトリはデフォルトで`/tmp`が使用される。
        * テンポラリディレクトリはJavaの起動パラメータ`-Djava.io.tmpdir=/path/to/temp`で変更可能。

以上
