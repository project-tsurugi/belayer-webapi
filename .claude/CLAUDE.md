# belayer-webapi

Belayer の **WebAPI サーバ本体**（Java / Spring Boot 2.7.x、WebFlux）。OSS。
API の実装・認証認可・ジョブ管理はすべてここにある。WebAdmin と RemoteCLI は `belayer` 側。

## この作業場の約束

- **push には慎重な姿勢を取る。** 基本的な作業手順として AI が `git push` を実行することはしない。
  手順として明確に指示された場合を除き、コミットまでで止めて報告する。
  force-push、リモートのブランチ・タグの作成／削除も同じ扱いとする。
- **コードのコメントは英語で書く。** 日本語で書くのは `.md` だけ。
- **Phase は統合せず、すべて分離して実施する。** 機能追加・不具合修正・テスト修正は
  別々のコミットにする。1コミットに2つの理由を混ぜない。
- **不具合は勝手に直さない。** 見つけたら報告して、起票するかどうかの判断を仰ぐ。

## 設計の入口

**`webapi/src/main/java/com/tsurugidb/belayer/webapi/config/RouterPath.java` が一次情報。**
この enum が「パス・パスマッチ・必要権限」を1か所で定義し、`Router`（ルーティング）と
`SecurityConfig`（認可）の両方が参照する。**エンドポイントを追加するときはまずここに1行足す。**

| パッケージ | 役割 |
|:--|:--|
| `api/` | ハンドラ |
| `config/` | `RouterPath`、`Router`、`FunctionPermission`、エラーハンドラ |
| `security/` | `SecurityConfig`（認可）、`PermissionConfig`（権限→ロール）、`RoleConfig`（ロール→ユーザ） |
| `service/` | 業務ロジック。`TsubakuroService` の実装／スタブは `service/tsubakuro/` |
| `exec/` | 外部コマンド（`tgctl_w` / `tgha_w`）を起動する実行系 |
| `model/` | `JobManager`、`MonitoringManager`（WatchService の常駐監視）、`FileWatcher`（モニタファイル1本の追記行を `ExecStatus` に変換） |

## 認証・認可の効き方

- **Harinoki へ出るのは `/api/auth`（発行）と `/api/refresh`（更新）だけ。**
- **Bearer の検証は `ticketProvider.restore()` でローカル完結する**（毎リクエストの問い合わせは無い）。
  したがって **Harinoki が落ちても既存トークンは有効期限（既定10分）まで操作が通る。**
- `permission.defaultRole`（既定 `ROLE_USER`）は fallback ではなく、
  **認証を通った全ユーザに無条件で付与される**。そこに割り当てた権限は事実上全開放。
- 権限を追加するときは `FunctionPermission` → `permission.properties` → `RouterPath` →
  `docs/belayer_authorization.md` の4点。**`RouterPath` から参照されない権限名を
  `permission.properties` に書くと起動に失敗する**ので、両者は必ず同時に更新する。

## 踏みやすい点

- **起動時に検査するのは `jq` と `${TSURUGI_HOME}/bin/tgctl` の2つだけ**
  （`webapi.required.commands`）。実際に呼ぶのは `tgctl_w` / `tgha_w` のほうで、
  これらは未検査。配置漏れでも**起動は通り、DB 操作だけ全部失敗する。**
    - **これは意図的に現状のままとする。** `tgctl_w` / `tgha_w` は、TsurugiDB 側の
      HA コマンドが実装されるまでの暫定ラッパーである。検査対象に加えると、
      `TSURUGI_TGCTL_COMMAND` / `TSURUGI_TGHA_COMMAND` で正式なコマンドに
      差し替えた環境で不要な起動失敗を招く。`python3` の検査を足さないのも同じ理由。
- **スタブは Python 3 製**（`distribution/stub/{tgctl_w,tgha_w}`、shebang は `/usr/bin/python3`）。
  python3 の存在は `install.sh` でも起動時検査でも確認していない。README には要件として書いてある。
- `tgctl_w` は本物の `tgctl` を呼ぶ**ラッパー**、`tgha_w` は外部コマンドを一切呼ばない**純粋なスタブ**。
  `mode.txt` はスタブ同士の内部状態で **Java 側は参照しない。**
- `jq` を実際に使うのは JAR 同梱の `resources/scripts/show_db_status.sh` だけ（`/api/db/status` 用）。
- **management ポートは既定では開かない**（2026-10-21〜）。`BELAYER_MANAGEMENT_ENABLED=true` のときだけ
  `BELAYER_MANAGEMENT_PORT`（既定 18000）を開く。実装は `config/ManagementPortEnvironmentPostProcessor`
  （`META-INF/spring.factories` で登録）で、OFF のとき `management.server.port=-1` にする。
  swagger-ui / openapi もこちら側に出る（`springdoc.use-management-port=true`）ので、**OFF のときは
  springdoc も止める。止めないと springdoc の Swagger UI 設定が起動時に落ちる。**
  開いたときの URL は `/management/swagger-ui`（`/management/webjars/swagger-ui/index.html` へ転送）と
  `/management/openapi/springdocDefault`。`/management/openapi` そのものは 404。
  management ポートにも Spring Security のフィルタは掛かるが、`/management/**` は `permitAll`。
- **`docker/` と `docker-compose.yml` はメンテナンスを止めている。別タイミングで見直す
  予定なので、それまで手を付けないこと。** `docker/Dockerfile` が
  `build/libs/belayer-webapi-<version>.jar` を移動しようとするが、成果物は
  `tsurugi-belayer.jar` に改名されており、builder 段で失敗する（最終更新 2023-10）。
  `test_work/e2e/` もこの compose に依存しているので同じ扱い。
- `distribution/` は `belayer/distribution/` と同名ファイルが重複している。
  **OSS 単体配布（`tsurugi-webapp-<ver>.tar.gz`）に入るのはこちら側。**
- CI（`ci-build.yml`）は `./gradlew check` を実行する（ユニットテストを含む）。

## Java のバージョン

tsubakuro と同じ型に統一済み。`toolchain 17` でビルドし、`options.release = 11` で
言語レベルと API 面の両方を 11 に縛る。CI の `java-version` も 17。
起動時検査の閾値は 11（`webapi.required.java_major_version`、環境変数
`BELAYER_REQUIRED_JAVA_MAJOR_VERSION` で上書き可）。1.5.3 で旧名 `java_majar_version` の誤字を
直したので、旧名で設定していた環境は無言で既定値 11 に戻る。

javadoc は JDK 17 の doclint で**警告もエラーも0件**にしてあり（2026-10-21）、
`javadoc.failOnError = false` は外した。`build` のたびに javadoc が走るので、
Javadoc のエラーでビルドが止まる。公開 API を足したら Javadoc も書くこと。

## テスト

`gradlew test` で `FileSystemApiHandlerTest.testDownloadZipFile_to_csv_file` と
`testDownloadZipFile_csv_parquet_conv_file` が **WSL の `/mnt/c` 上では落ちる。タイミング問題で、
コードとは無関係。** `@AutoConfigureWebTestClient` の既定 5 秒に対し、parquet の CSV 変換が
5.07〜5.09 秒かかっている（`/mnt/c` は parquet の細かい読み取りが遅い）。2026-10-21 には
再実行しても2件とも落ちたが、同じコミットを WSL 側のディスクに展開して流すと 2.8 秒で全件通った。
**`/mnt/c` で落ちたら WSL 側にコピーして確かめる。**
