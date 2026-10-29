---
name: belayer-webapi-release
description: belayer-webapi をリリースする。リリース番号の確認（docs/release.md から類推、既存タグの扱い）、tsubakuro の更新要否の確認、リリースノートとコミットログの突き合わせ（結果を .release_check.md に出す）、リリースノートの確定とコミット、リリースタグの作成までを行う。「belayer-webapi をリリースして」「リリースの準備をして」「リリースタグを付けて」と頼まれたときに使う。
disable-model-invocation: true
---

# belayer-webapi のリリース

belayer-webapi のリリースを、ローカルのリポジトリで**タグを付けるところまで**行う。
リリースそのもの（GitHub Release と配布物の作成）は、タグの push を契機に `.github/workflows/ci-release.yml` が行う。

## 前提と約束

- **push はしない。** コミットの push もタグの push もユーザーが行う。リモートのタグ・GitHub Release の削除もユーザーが行う。
  このスキルの最後に、ユーザーが実行するコマンドを示す。
- リリースノートは **`docs/release.md`**（英語）。各版の節は `## ver X.Y.Z (Since ver A.B.C)` で始まり、
  `### Breaking changes` / `### Bug fixes` / `### Misc changes` / `### Full Changelog` の小見出しを持つ（無い小見出しは省く）。
- **まだリリースしていない版の節は、見出しの末尾に ` - unreleased` を付ける**（例: `## ver 1.5.4 (Since ver 1.5.3) - unreleased`）。
  開発中にエントリを足すときはこの形で節を作り、リリースのときにこのスキルが外す。1.5.3 以前の節には付いていない。
- 版数は2か所にある。**`VERSION`**（`build.gradle` が読み、JAR と OpenAPI の版になる）と、タグ名。
  CI はタグ名で `VERSION` を上書きしてビルドするが、タグを付けるコミットの `VERSION` もリリース番号に揃えておく。
- tsubakuro の版は `webapi/build.gradle` の `tsubarkuro_version`（変数名は原文のまま）。
- 既存のタグは注釈付きタグ（`git tag -a`）。`X.Y.Z-snapshot` のような snapshot のタグはリリースとして数えない。
- 変更は理由ごとに別のコミットにする（tsubakuro の更新、`VERSION`、リリースノート）。
  コミットメッセージは英語で、既存の形（`build: ...`、`chore: ...`、`docs: ...`）に合わせる。
- 途中でユーザーの確認が要る箇所（下の「**確認**」）では、必ず止まって回答を待つ。勝手に進めない。

## 手順

### 1. 作業ツリーの確認

- `git status --short` が空で、ブランチが `master` であることを確かめる。そうでなければ止めて報告する。
- `git fetch --tags origin` でリモートのタグを取り込む（push ではないので実行してよい）。
- `git rev-list --count '@{u}..HEAD'` で未 push のコミット数を数え、報告に含める。

### 2. リリース番号の確認

1. `docs/release.md` の先頭の節の見出しから、版（X.Y.Z）と前の版（Since の A.B.C）を読む。
2. `VERSION` の値と、リリースのタグ（snapshot を除く）の一覧 `git tag --sort=-v:refname` を見る。
3. 次のように類推して、**リリース番号の案を1つ**示す。
    - 先頭の節の版にまだタグが無い → その版。
    - 先頭の節の版に既にタグがある → リリースノートに新しい節がまだ無い。前の版の次のパッチ版を案にし、
      手順4で節を作る必要があることも伝える。
    - `VERSION` と食い違うときは、食い違いを示す（`VERSION` は手順5で揃える）。
    - Since の版が直前のリリースタグと違うときも示す。
4. **既存タグの確認。** 案の版のタグがあるか、ローカル（`git tag -l <版>`）とリモート（`git ls-remote --tags origin <版>`）の両方で調べる。
    - ローカルにだけある → 削除してよいか**確認**する。了承されたら `git tag -d <版>` で消す。
    - リモートにもある → このスキルでは消さない。リモートのタグと GitHub Release を消すかどうかをユーザーに**確認**し、
      消す場合はユーザーが実行するコマンドを示す（`git push origin :refs/tags/<版>`、`gh release delete <版> --repo project-tsurugi/belayer-webapi`）。
      ユーザーが消し終えるまで手順7には進まない。
5. リリース番号を**確認**する（案のままか、別の版か）。

### 3. tsubakuro の更新要否の確認

1. 今の版を `webapi/build.gradle` の `tsubarkuro_version` から読む。
2. Maven Central の最新を調べる:
   `curl -s https://repo1.maven.org/maven2/com/tsurugidb/tsubakuro/tsubakuro-session/maven-metadata.xml`
   の `<release>` と `<version>` の一覧。今の版より新しい正式版（`-SNAPSHOT` でないもの）を挙げる。
3. 類推の材料として、前のリリースからの tsubakuro の変更（`git log <前のタグ>..HEAD -- webapi/build.gradle`）と、
   過去のリリースノートでの tsubakuro の更新の書き方（例: `update tsubakuro dependencies to version 1.16.0.`）を見る。
4. 「更新しない」か「X に更新する」かの案と理由を示し、**確認**する。
   新しい版があっても、組み合わせる TsurugiDB の版で決まるので、ユーザーの判断を仰ぐ。
5. 更新する場合:
    - `tsubarkuro_version` を書き換え、`webapi` で `./gradlew build` を通す（WSL では `--console=plain`、`-q` とリダイレクトを組み合わせない）。
    - コミットする（例: `build: update tsubakuro to 1.17.0`）。
    - リリースノートの `### Misc changes` に `* update tsubakuro dependencies to version X.` を足す（手順4のコミットに含める）。

### 4. リリースノートとコミットログの突き合わせ

1. 対象のコミットを集める: `git log --no-merges --format='%h %s' <前のリリースタグ>..HEAD`。
   本文も要るときは `git show --stat <hash>` で見る。
2. コミットごとに、リリースノートのどのエントリに当たるかを対応付ける。
    - 利用者に見える変更（API の応答・パス・権限、設定項目・環境変数、配布物・スクリプト、必要な環境、セキュリティ）は
      エントリが要る。挙動を変える修正は `Breaking changes` に入れるべきかも見る。
    - 内部だけの変更（テスト、CI、`.claude/`、コメント、devcontainer など開発環境）はエントリが無くてよい。
      ただし、まとめて1行にしている既存の書き方（`clean up the code: ...`）があれば、それに含まれるかを見る。
    - revert で打ち消し合うコミットの組は、差し引きで判断する。
3. 逆向きに、エントリごとに裏付けのコミットがあるか（書いてあるのに実装が無い・違う、が無いか）を見る。
4. 表記も見る: 版と Since、`Full Changelog` のリンク（`compare/<前の版>...<リリース番号>`）、小見出しの並び、
   見出しの ` - unreleased`（無ければ付け忘れとして報告するだけでよい）、残っている `TBD` / `TODO` / `(WIP)` など。
5. 結果を **`.release_check.md`**（リポジトリの直下。`.gitignore` 済みでコミットしない）に書き、
   同じ要点を画面にも出す。書く内容:
    - リリース番号、前のリリースタグ、対象コミット数、未 push のコミット数
    - tsubakuro: 今の版、最新の版、判断
    - コミット → エントリの対応表（`hash | 件名 | 対応するエントリ / 不要の理由`）
    - エントリが足りないもの、エントリが余っている・食い違っているもの、表記の問題
    - **確認したい点**（番号付き。それぞれに案を添える）
6. 疑問点は**まとめて**示し、回答を**確認**する。1件ずつ小出しにしない。

### 5. リリースノートの確定とコミット

回答を受けてから行う。

1. `docs/release.md` のリリースする版の節を直す: 足りないエントリを足し、誤りを直し、
   見出しの ` - unreleased` を外し、見出しの版と Since、`Full Changelog` のリンクを確定する。
   既存のエントリの書き方（英語、現在形の動詞で始める、利用者から見た影響を書く）に合わせる。
2. `VERSION` がリリース番号と違えば直す。
3. コミットは理由ごとに分ける。
    - `VERSION`: `chore: bump the version to X.Y.Z`
    - リリースノート: `docs: finalize the X.Y.Z release note`
   変更が無いものはコミットしない。

### 6. ビルドの確認

`webapi` で `./gradlew build` を流し、通ることを確かめる。落ちたら止めて報告する（直さない）。
`FileSystemApiHandlerTest` の zip ダウンロードの2件は、Windows のフォルダ（WSL の `/mnt/c` や、そこをマウントしたコンテナ）では
時間切れで落ちることがある。そのときは Linux 側のディスクに複製して流し直して確かめる。

### 7. リリースタグの作成

1. 手順2で確かめた版のタグが無いことを、もう一度確かめる。
2. `git tag -a <版> -m "<版>"` で、`HEAD`（手順5のコミットを含む `master`）に注釈付きタグを付ける。
3. `git show --no-patch <版>` でタグの指す先を確かめる。

### 8. 報告

画面に次を出す。

- 作ったコミット（ハッシュと件名）とタグ
- `.release_check.md` の確認点のうち、回答を受けて反映したもの
- **ユーザーが行うこと**（この順で）
    1. `git push origin master`（タグの前にコミットを push する）
    2. `git push origin <版>`（`ci-release.yml` が動き、GitHub Release と配布物ができる）
    3. GitHub Release の本文に `docs/release.md` の該当の節を貼る（CI は本文を書かない）
    4. belayer 側で `WEBAPI_VERSION` を上げる（belayer の配布物に入るのはその後）
