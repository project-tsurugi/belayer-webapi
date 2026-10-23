# belayer-webapi - 開発コンテナ (VSCode Dev Containers)

TsurugiDB の公式 Docker イメージ（`ghcr.io/project-tsurugi/tsurugidb`）をベースにした開発コンテナです。
**TsurugiDB と WebAPI を同じコンテナで動かします。** WebAPI は TsurugiDB に IPC で接続し、
`tgctl` / `tgctl_w` / `tgha_w` をローカルで実行するため、別コンテナには分けられません。

| 項目 | 内容 |
|:--|:--|
| ベースイメージ | `ghcr.io/project-tsurugi/tsurugidb:1.12.0`（Ubuntu 22.04、Java 17 の JDK、Python 3.10 を含む） |
| 追加するもの | jq、git、sudo など（`Dockerfile`） |
| 実行ユーザ | `tsurugi`（パスワードなしで sudo できる） |
| TsurugiDB | コンテナの起動と同時に起動する（イメージのエントリポイント）。`TSURUGI_HOME=/usr/lib/tsurugi` |
| 認証 | WebAPI は `authmock` プロファイルで動かす。認証サーバ（harinoki）は起動しない |

TsurugiDB の版は `docker-compose.yml` の `TSURUGI_VERSION` で変えられます。

## 開発環境のセットアップ

1. [VSCode](https://code.visualstudio.com/) と [Docker Desktop](https://docs.docker.com/get-docker/) をインストール
2. VSCode に拡張機能「[Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)」をインストール
3. 次のどちらかで開く
    * コマンドパレットから「Dev Containers: Clone Repository in Container Volume」を選び、belayer-webapi のリポジトリ URL を入力
    * 手元に clone したフォルダを開き、「Dev Containers: Reopen in Container」
4. コンテナの作成後に `.devcontainer/post-create.sh` が1回だけ動き、`install.sh` が行う配置を作業ツリーから行う
    * `distribution/stub/tgctl_w` / `tgha_w` → `${TSURUGI_HOME}/bin/`（実行権限を付ける）
    * `distribution/config/endpoints.csv` / `instance_info.json` → `${BELAYER_HOME}/config/`（既にあれば上書きしない）
    * スタブを書き換えたときは、このスクリプトを再実行する（`bash .devcontainer/post-create.sh`）

## 環境変数

`.devcontainer/.env` でコンテナ全体に設定します。WebAPI の設定項目は [README](../README.md#application-settings) の表を参照してください。

| 変数 | 値 | 説明 |
|:--|:--|:--|
| `SPRING_PROFILES_ACTIVE` | `authmock` | 認証をモックにする。ユーザとパスワードは `webapi/src/main/resources/application-authmock.properties`（例: `tsurugi` / `password`） |
| `BELAYER_HOME` | `/opt/belayer` | `config/` と、起動時にスクリプトを展開する `proc/` を置く |
| `BELAYER_STORAGE_ROOT` | `/opt/belayer/storage` | ダンプ・バックアップなどのファイルの置き場所 |
| `BELAYER_LOG_LEVEL` | `INFO` | `DEBUG` にすると、スタブと状態取得スクリプトのログ（`/tmp/*_history.log` など）も出る |
| `BELAYER_MANAGEMENT_EXPOSURE` | `health,prometheus,info,env,beans,openapi,swagger-ui` | **開発用。** management ポート（18000）で actuator の全項目と OpenAPI・Swagger UI を公開する（既定は `health,prometheus` だけ）。認証なしで読めるので、本番の設定には使わないこと |

## ビルドとテスト

```sh
cd /workspaces/belayer-webapi/webapi
./gradlew build            # テストと javadoc を含む
./gradlew build -x test    # テストを省く
```

Gradle のキャッシュは名前付きボリューム（`gradle-cache`）に置くので、コンテナを作り直しても残ります。

## WebAPI の起動

```sh
cd /workspaces/belayer-webapi/webapi
./gradlew build -x test
java -jar build/libs/tsurugi-belayer.jar
```

VSCode の Spring Boot Dashboard（拡張機能 `vmware.vscode-boot-dev-pack`）から起動・デバッグすることもできます。
環境変数はコンテナに設定済みなので、追加の設定は要りません。

### 動作確認

```sh
curl http://localhost:8000/api/hello

# authmock のユーザでログインしてトークンを取得
TOKEN=$(curl -s -X POST -d 'uid=tsurugi&pw=password' http://localhost:8000/api/auth | jq -r .accessToken)

# DB の状態（tgctl_w / tgha_w を使う）
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/db/status

# management ポート
curl http://localhost:18000/management/health
```

ポート 8000（WebAPI）、18000（management）、12345（TsurugiDB の TCP）は、VSCode がホストへ転送します。
Swagger UI はホストのブラウザから <http://localhost:18000/management/swagger-ui> で開けます。

## TsurugiDB

* 設定ファイル: `/usr/lib/tsurugi/var/etc/tsurugi.ini`（公式イメージの設定。認証は無効）
* データ: `/opt/tsurugi/var/data`（名前付きボリューム `tsurugi-data`。コンテナを作り直しても残る）
* ログ: コンテナのログ（VSCode の外から `docker logs <コンテナ名>`）
* SQL コンソール: `tgsql -c ipc:tsurugi`
* 状態の確認: `tgctl status`

WebAPI から TsurugiDB を停止・起動（`/api/db/shutdown`、`/api/db/start`）すると、このコンテナ内の TsurugiDB が止まり、また起動します。
コンテナ自体は止まりません（エントリポイントは TsurugiDB の停止を待たずに動き続ける）。

### 認証サーバ（harinoki）を使う場合

公式イメージには harinoki が含まれていますが、`tsurugi.ini` の `[authentication]` で `enabled=true` にしたときだけ、
エントリポイントが起動します。harinoki で認証を確かめたいときは、次のようにします。

1. `/usr/lib/tsurugi/var/etc/tsurugi.ini` の `[authentication]` に `enabled=true` を書く
2. ユーザは `/usr/lib/tsurugi/var/auth/etc/harinoki-users.props` に書く
3. コンテナを再起動する（エントリポイントが harinoki と TsurugiDB を起動する）
4. `SPRING_PROFILES_ACTIVE` を外して（または `default` にして）WebAPI を起動する

この変更はコンテナの中だけに残り、コンテナを作り直すと元に戻ります。
