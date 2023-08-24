# Getting Started with Belayer

## インストール

* README.mdの[5.Create Distributions](../README.md#5-create-distributions)を参照し、Belayerの配布物を作成する。
* README.mdの[6.Installing Distributions](../README.md#6-installing-distributions)を参照し、Belayerをインストールする。
    * Belayer WebAPI Server(WebAdminを含む)はTsurugiDBと同じマシンにインストールする。
        * TsurugiDBとBelayerは同一のファイルシステムを共有し、互いに出力したファイルにアクセスするため。
    * Remote CLIは、Belayer WebAPI Serverの公開ポート(デフォルトは8000ポート)にネットワークアクセスが可能なマシンにインストールする。

## サーバの起動と稼働確認

1. WebAPI Serverの起動確認
    * 以下のコマンドでWebAPI Serverへの疎通を確認する。
        ```console
        $ curl <host_ip>:8000/api/hello
        hello
        ```
    * 接続できない場合は、WebAPI ServerのIPアドレスとポートやWebAPIServerのプロセスの起動状態を確認する。
2. WebAPI Serverと認証サーバとの疎通確認
    * 以下のコマンドでWebAPIでの認証が正常になされることを確認する。
        ```console
        $ curl -v -d uid=tsurugi -d pw=password <host_ip>:8000/api/auth
        ```
    * ステータス:200でAccessToken,RefreshTokenがJSONで返却されればOK。
    * エラーになる場合、Belayer WebAPIサーバのログ（起動時の標準出力）を確認し、認証サーバ(harinoki)の起動状態や疎通を確認する。
        * 認証サーバのURLは`TSURUGI_AUTH_URL`で指定する。
3. WebAdminとWebAPI Serverとの疎通確認
    * 以下のURLを開き、ログイン画面が表示されることを確認する。
        * http://<host_ip>:8000/admin
    * ログイン画面にてユーザIDとパスワードを入力し、ログインできることを確認する。
    * もしログイン画面が表示されない、ログインできない事象が発生した場合、「1.」「2.」を再確認する。
4. WebAdminでのバックアップの実行確認
    * WebAdminでログイン後、再度メニューのBackup/RestoreのBackupを選択する。
    * ディレクトリパス（例：bk1)を入力し、「START BACKUP」ボタンを押下する。
    * Confirmダイアログが出るので、「CONFIRM」を押下する。
    * バックアップのジョブ一覧に「Status」が「RUNNING」のジョブが追加されることを確認する。
    * 画面右上のリロードボタン（ユーザ名の左隣り）を押してステータスがCOMPLETEDになればOK。
    * もし、バックアップジョブが「FAILED」になる場合、WebAPIサーバのログを確認する。
        * WebAPI Serverを起動する際に、以下の[環境変数](../README.md#application-settings)がインストール環境に適合しているか確認する。
            * BELAYER_SERVER_PORT
            * TSURUGI_URL
            * TSURUGI_HOME
            * TSURUGI_CONF
            * BELAYER_STORAGE_ROOT
5. Remote CLIの疎通確認
    * 以下のコマンドを実行し、Remote CLIとWebAPI Server間の疎通を確認する。
        ```console
        tsurugi-fs list -url http://<host_ip>:8000 -u tsurugi -p password -dir .
        ```
    * tsurugi-fsのパスが通っていない場合は環境変数PATHにRemoteCLIを展開したディレクトリを追加する。

　

以上

