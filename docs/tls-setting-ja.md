
# TLS設定

## 概要

Belayer WebAPI をTLSで公開する場合の手順を以下に示す。

以下のステップで進める。
ステップ1は2つの方法を提示している。どちらか一方を選択する。

* ステップ0: 準備
    * CA証明書とサーバ証明書の作成と配置
* ステップ1: WebAPIサーバのTLS適用
    * パターン①: WebAPIサーバ自体でTLSを有効化する
    * パターン②: リバースプロキシを立て、リバースプロキシがTLSの解決を行う
* ステップ2: WebAPIサーバにTLSでアクセスする


## ステップ0: 準備

### CA証明書とサーバ証明書の作成と配置

TLSを有効化するには、サーバ証明書を取得する必要がある。

サーバ証明書は、なんらかの既存のCA(認証局)から発行する(※1)場合と、独自でCAを作成し、そこからサーバ証明書を発行する場合がある。

※1 公的なCAから発行してもらう場合や組織で運用しているCAから発行する場合がこれにあたる。

ここでは、TLS/PKIツールである[cfssl](https://github.com/cloudflare/cfssl)と`openssl`を使用して、
独自のCA証明書を作成し、作成したCA証明書を使ってサーバ証明書を作成する手順を示す。

### CA証明書の作成

ここでは例として、CA証明書を作成するための鍵を作成し、その鍵で自己署名した独自のCA証明書を作成する。
CA用の鍵とCA証明書を作成するサンプルスクリプトは [create_ca.sh](../webapi/misc/cert/create_ca.sh) にある。

証明書に設定する`CN`などの項目は適宜、変更する。

```sh
cd webapi/misc/cert
./create_ca.sh
```

スクリプトを実行すると、以下の2つのファイルが生成される。

* my-ca-key.pem
    * CAの鍵ファイル(ECDSA256形式)
* my-ca.pem
    * CAの証明書

CAの証明書は、サーバ証明書の妥当性をチェックする際に必要になるので、クライアント（ユーザエージェント）に配布する。
CAの鍵ファイルは秘匿すべき情報のため、厳密に管理する必要がある。


### サーバ証明書の作成

前段で作成したCA証明書とCA鍵を使って新規のサーバ証明書を作成する。

サーバの鍵とサーバ証明書を作成するサンプルスクリプトは [create_server_certs.sh](../webapi/misc/cert/create_server_certs.sh) にある。

スクリプトにある通り、サーバ証明書の作成には「CAの鍵」と「CA証明書」を使用する。
`hosts`項目には実際にアクセスするホスト名/IPアドレスと一致させる必要があるので、適宜変更する。

```sh
./create_server_certs.sh
```

スクリプトを実行すると、以下の3つのファイルが生成される。

* web-api-server-key.pem
    * サーバ証明書用の鍵ファイル(ECDSA256形式)
* web-api-server.pem
    * サーバ証明書ファイル
* server-cert.p12
    * サーバ証明書用の鍵とサーバ証明書のペアをPKCS#12形式でパッケージングしたファイル
    * 上記のスクリプトではパスワードなしでパッケージングしている
な
TLSを有効化するためには、Webサーバにサーバ証明書とその鍵を設定する必要がある。
サーバ証明書とその鍵を一体化して扱うケースのためにPKCS#12形式のファイルを作成しているが、一体化させて使用するケースがなければ使用する必要はない。

サーバ証明書は公開するものであり、秘匿する必要はない。
サーバ証明書を作成する際に使用した「鍵」は厳密に管理する必要がある。

## ステップ1: WebAPIサーバのTLS適用

以下のいずれかの方法でTLSを有効化する。
両方の設定を混在させる必要はない。

* パターン①: WebAPIサーバ自体でTLSを有効化する
* パターン②: リバースプロキシを立て、リバースプロキシがTLSの解決を行う


### パターン①: WebAPIサーバ自体でTLSを有効化する

前段で作成したサーバ証明書と鍵のセットである`server-cert.p12`を`/opt/cert/`に配置する。
belayerをインストールしたディレクトリのbinに存在する起動スクリプトを以下のように書き換え、TLSを有効する。
Javaの起動パラメータでSpringの関連プロパティの値を指定する。
ここで、TLSのプロトコルやサーバ証明書用のPKCS12形式のファイルのパスを指定する。

* /usr/libb/tsurugi-webapp-<version>/bin/start_server.sh

```sh
#BELAYER_JAVA_OPTS="${BELAYER_JAVA_OPTS}"
# ↑ コメントアウト ↓ この行を追記する
BELAYER_JAVA_OPTS="--server.port=443 --server.ssl.enabled=true --server.ssl.protocol=TLSv1.2 --server.ssl.key-store-type=PKCS12 --server.ssl.key-store=file:/opt/cert/server-cert.p12 --server.ssl.key-store-password="

$_JAVA_PATH -jar ${JAR} ${BELAYER_JAVA_OPTS} 
```

このスクリプトで起動するとBelayer WebAPIサーバがポート443でhttpsをLISTENする状態になる。

### パターン②: リバースプロキシを立て、リバースプロキシがTLSの解決を行う

Ubuntuにnginxをインストールしてリバースプロキシをセットアップする例を以下に示す。

```sh
apt install nginx
```

前段で作成したサーバ証明書と鍵を`/opt/cert/`に配置する。

* /opt/cert/web-api-server.pem
* /opt/cert/web-api-server-key.pem


以下のファイルを新規で作成する。
ここではリバースプロキシとしてTLSでリクエストを受け付け、`localhost:8000`でHTTPをリッスンしているbelayerに転送するように設定する。


* /etc/nginx/sites-available/belayer-proxy

```
server {
    listen  443 ssl;
    server_name  _;
    ssl_certificate /opt/cert/web-api-server.pem;
    ssl_certificate_key /opt/cert/web-api-server-key.pem;

    location / {
        proxy_pass http://localhost:8000;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade $http_upgrade;
        proxy_set_header   Connection keep-alive;
        proxy_set_header   Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
     }

    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   /usr/share/nginx/html;
    }
}
```

nginxのデフォルトで配置されている設定をOFFにするため、`/etc/nginx/sites-enabled/` に作成されている
`default`のシンボリックリンクを削除する。
また、新規で作成した設定ファイルを有効化するため、`/etc/nginx/sites-enabled/` に作成した`belayer-tls`のシンボリックリンクを`/etc/nginx/sites-enabled/`に作成し、nginxを再起動する。

```
sudo rm /etc/nginx/sites-enabled/default
sudo ln -s /etc/nginx/sites-available/belayer-proxy /etc/nginx/sites-enabled/belayer-proxy
sudo systemctl restart nginx
```

これで、nginxがポート443でhttpsリクエストをLISTENするようになる。

## ステップ2: WebAPIサーバにTLSでアクセスする

WebAPIにhttpsでアクセスし、アクセス可能なことを確認する。

ここでは、独自CAから発行したサーバ証明書を組み込んだサーバにアクセスするため、
`curl`のオプションでCA証明書のファイルパスを指定する。

※ CA証明書を指定することで、当該CAがサインしたサーバ証明書をすべてverifyすることができる。

※ 公的機関のCAからサーバ証明書が発行されている場合、CA証明書はOSに組み込まれているため、CA証明書の指定は不要。

```sh
curl -v --cacert ./my-ca.pem https://localhost:443/api/hello
```

以下のようにサーバ証明書の妥当性が確認できていることが表示される。

* `Server certificate:`の周辺に`SSL certificate verify ok.`と表示される

```sh
*   Trying 127.0.0.1:443...
* Connected to localhost (127.0.0.1) port 443 (#0)
* ALPN, offering h2
* ALPN, offering http/1.1
*  CAfile: ./my-ca.pem
*  CApath: /etc/ssl/certs
* TLSv1.0 (OUT), TLS header, Certificate Status (22):
* TLSv1.3 (OUT), TLS handshake, Client hello (1):
* TLSv1.2 (IN), TLS header, Certificate Status (22):
* TLSv1.3 (IN), TLS handshake, Server hello (2):
* TLSv1.2 (IN), TLS handshake, Certificate (11):
* TLSv1.2 (IN), TLS handshake, Server key exchange (12):
* TLSv1.2 (IN), TLS handshake, Server finished (14):
* TLSv1.2 (OUT), TLS header, Certificate Status (22):
* TLSv1.2 (OUT), TLS handshake, Client key exchange (16):
* TLSv1.2 (OUT), TLS header, Finished (20):
* TLSv1.2 (OUT), TLS change cipher, Change cipher spec (1):
* TLSv1.2 (OUT), TLS header, Certificate Status (22):
* TLSv1.2 (OUT), TLS handshake, Finished (20):
* TLSv1.2 (IN), TLS header, Finished (20):
* TLSv1.2 (IN), TLS header, Certificate Status (22):
* TLSv1.2 (IN), TLS handshake, Finished (20):
* SSL connection using TLSv1.2 / ECDHE-ECDSA-AES256-GCM-SHA384
* ALPN, server did not agree to a protocol
* Server certificate:
*  subject: CN=api.example-tls.tk
*  start date: Feb 12 20:57:00 2024 GMT
*  expire date: Feb  9 20:57:00 2034 GMT
*  subjectAltName: host "localhost" matched cert's "localhost"
*  issuer: CN=example-tls.tk
*  SSL certificate verify ok.
* TLSv1.2 (OUT), TLS header, Supplemental data (23):
> GET /api/hello HTTP/1.1
> Host: localhost
> User-Agent: curl/7.81.0
> Accept: */*
> 
* TLSv1.2 (IN), TLS header, Supplemental data (23):
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Date: Fri, 16 Feb 2024 08:33:14 GMT
< Content-Type: text/plain
< Cache-Control: no-cache, no-store, max-age=0, must-revalidate
< Pragma: no-cache
< Expires: 0
< X-Content-Type-Options: nosniff
< Strict-Transport-Security: max-age=31536000 ; includeSubDomains
< X-Frame-Options: DENY
< X-XSS-Protection: 1 ; mode=block
< Referrer-Policy: no-referrer
< Content-Length: 5
< 
* Connection #0 to host localhost left intact
```

