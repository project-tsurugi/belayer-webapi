# Getting Started with Belayer-WebAPI

## インストール

* README.mdの[5.Create Distributions](../README.md#5-create-distributions)を参照し、Belayerの配布物を作成する。
* README.mdの[6.Installing Distributions](../README.md#6-installing-distributions)を参照し、Belayerをインストールする。
    * Belayer WebAPI ServerをTsurugiDBと同じマシンにインストールする。
        * TsurugiDBとBelayerは同一のファイルシステムを共有し、互いに出力したファイルにアクセスするため。

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

　

以上

