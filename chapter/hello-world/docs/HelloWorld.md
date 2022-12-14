---
marp: true
---
# http4sでのHello World

公式ドキュメント: https://http4s.org/
Github: https://github.com/http4s/http4s

---

# 目的

最初から理解するのは難しいので、最初はこんな感じで書いてアプリケーション作っていくのねえ〜ぐらいの感覚で大丈夫です。

http4を使う上でCats Effectがどのように絡んでくるのかとかも見ていただければと思います。

---
# http4sとは

> Typeful, functional, streaming HTTP for Scala

Scalaのためのタイプフル、関数的、ストリーミングHTTP

http4sはtypelevelエコシステムのシンプルなhttp(サーバー、クライアント)ライブラリです。
IO モナドなどのエフェクトライブラリと組み合わせて使います.

---
# サーバー/クライアント

http4sのサーバー/クライアントは以下表にあるものから選んで構築を行うことができます。

![height:500](../images/Backend-Integrations.png)

---
# サーバー/クライアント
以前までBlazeがメインサーバーでしたが、現在はEmberというものがメインで開発を行っているサーバーになります。

※ サンプルコードや記事ではBlazeServerを使うものが多いですが、最新はEmberServerなので使用する時は注意してください。

---

# 構成

http4はざっくり言うとパスごとに処理を書くサービスと、アプリケーションで実行するサービスをまとめたルーターと、ルーター(アプリケーション)を受け取りアプリケーションを実行するサーバーで構成されています。

サービス: HttpRoutes
ルーター: Router/HttpApp
サーバー: EmberServer (※ Emberを使用した場合)

---

# 依存関係の追加

依存関係に以下2つを追加します。

```scala
val http4sVersion = "0.23.16"
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion
)
```

---

# サービス

http4sのサービスを構築するためのHttpRoutesは、Kleisliの単純なエイリアスです。

```scala
Kleisli[[T] =>> OptionT[F, T], Request[F], Response[F]]
```

KleisliはRequest[F] => F[Response[F]]の便利なラッパーに過ぎず、Fは効果的な操作です。

※ Fは今回Cats EffectのIOを使用しますが、IOに関してはCats Effectの章で説明します。
※ =>> はScala3で追加されたLambda Functionというものです。

---

# Kleisliとは

Kleisliは `A => F[B]` という型の関数に対する特殊なラッパー

- [Catsのドキュメント](https://typelevel.org/cats/datatypes/kleisli.html)
- 圏論的に学びたい人は[こちら](https://criceta.com/category-theory-with-scala/04_Kleisli_category.html)を参照

---

# サービス

HttpRoutesを使用した最小サービス

これはメソッドがGETでパスが`/hello/takapi`の場合、200のレスポンスで`Hello, takapi.`を返すサービスを構築したことになる。

```scala
val helloWorldService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
}
```

---

# HttpRoutes.of[IO]

`of[F[_]: Monad]`メソッドは、部分関数を受け取りそれをHttpRoutesに昇格させるためのメソッドです。

```scala
def of[F[_]: Monad](pf: PartialFunction[Request[F], F[Response[F]]]): HttpRoutes[F] =
  Kleisli(req => OptionT(Applicative[F].unit >> pf.lift(req).sequence))
```

`Applicative[F].unit`は`pf.lift(req).sequence`の結果をFにliftしていると思ってください。

---

# PartialFunctionとは

MapやOptionのcollectメソッドの内部などで使われているもの

Aに対してBを返すような関数ですが、必ずしもタイプAのすべての値を含むとは限らないことに注意

```scala
trait PartialFunction[-A, +B] extends (A) => B
```

つまり、簡単にいうと特定の引数のみ処理する関数。

---

# PartialFunctionとは

普通の関数と同じように呼び出すが、パターンにマッチしない場合はMatchErrorになる。

```scala
val pf: PartialFunction[Int, String] = {
  case 1 => "first"
  ... // 複数定義可能
}

println(pf(1)) // => first
println(pf(2)) // => MatchError
```

---

# PartialFunctionとは

PartialFunctionは合成することができる。

```scala
val pf1: PartialFunction[Int, String] = { case 1 => "first" }
val pf2: PartialFunction[Int, String] = { case 2 => "second" }

// pf1にマッチしない場合はpf2を適用するPartialFunctionを生成
val pf3 = pf1 orElse pf2

println(pf3(1)) // => first
println(pf3(2)) // => second
println(pf3(3)) // => MatchError
```

---

# PartialFunctionとは

`of`メソッドの中で使われている`lift`はPartialFunctionのキーに該当してたらSomeに包んで値を返し、なければNoneを返すメソッド。

```scala
def of[F[_]: Monad](pf: PartialFunction[Request[F], F[Response[F]]]): HttpRoutes[F] =
  Kleisli(req => OptionT(Applicative[F].unit >> pf.lift(req).sequence))
```

---

# HttpRoutes

つまりHttpRoutesの構築は、リクエストを受け取りそのリクエストに一致したものがあればその値(Response)を返す関数だということがわかります。

---

# Requestなんてないやん

ここで最小の実装をもう1度見て見ましょう。

```scala
val helloWorldService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
}
```

これを見てパッと見どれがRequestかわかる人は少ないと思います。

実装は以下のようになっているのですが、ではどれがRequestなのでしょうか？
```scala
final class Request[F[_]] private (
  val method: Method,
  val uri: Uri,
  val httpVersion: HttpVersion,
  val headers: Headers,
  val body: EntityBody[F],
  val attributes: Vault,
) ...
```

---

# Requestなんてないやん

以下画像の赤枠で囲った部分が全てRequestになります。

![width:100%](../images/HttpRoutes_1.drawio.svg)

---

# どういうこと？

なんでこれがRequestになるの？と思ったかもしれません。
なので1つずつ分解していきましょう。

---

# Method -> Path

まずは`->`の部分に関して見ていきます。
これは結論から言うと、RequestをMethodとPathに分解するunapplyメソッドを持った抽出子objectです。

実装は以下のようになっています。

```scala
object -> {
  def unapply[F[_]](req: Request[F]): Some[(Method, Path)] =
    Some((req.method, req.pathInfo))
}
```

※ applyメソッドが引数を取りオブジェクトを作るコンストラクタであるように、unapplyは1つのオブジェクトを受け取り引数を返そうとするものです。

---

# Path / Path

次は`/`の部分に関して見ていきます。
こちらはRequestのPathを細かく分解するunapplyメソッドを持った抽出子objectです。

```scala
object / {
  def unapply(path: Path): Option[(Path, String)] =
    if (path.endsWithSlash)
      Some(path.dropEndsWithSlash -> "")
    else
      path.segments match {
        case allButLast :+ last if allButLast.isEmpty =>
          if (path.absolute)
            Some(Root -> last.decoded())
          else
            Some(empty -> last.decoded())
        case allButLast :+ last =>
          Some(Path(allButLast, absolute = path.absolute) -> last.decoded())
        case _ => None
      }
}
```
---

# Path / Path

`/`は`->`でRequestをMethodとPathに分解した後に、Pathを更に分解するものになります。

---

# unapply x パターンマッチの恩恵を受けずに書くと？

最小サービスの実装をunapply x パターンマッチの恩恵を受けずに書くと以下のようになります。

愚直に書くとなんとなくやってることがわかったんじゃないでしょうか？

```scala
def requestToResponse(request: Request[IO]): IO[Response[IO]] =
  ->.unapply(request) match
    case Some((method, path1)) if method.name == "GET" => /.unapply(path1) match
      case Some((path2, str)) => /.unapply(path2) match
        case Some((_, str1)) if str1 == "hello" => Ok(s"Hello, $str")
        case _ => NotFound("")
      case None => NotFound("")
end requestToResponse

val helloWorldService = HttpRoutes.of[IO] {
  case request => requestToResponse(request)
}
```

※ 本来はもっと条件分岐が必要になってきます。

---

# unapply x パターンマッチの恩恵を受けずに書くと？

愚直に書くととても実装が長くなってしまいますが、unapplyはパターンマッチで扱えるのでここにScalaの強力な型システムが組み合わさって、以下のようにとても少量のコードで同じような実装が実現できるのです。

```scala
val helloWorldService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
}
```

---

# つまり

http4sにはこのようにRequestに対しての抽出子オブジェクトが複数存在しています。

Requestに対しての抽出子オブジェクトを用意し、unapplyメソッドの1つのオブジェクトを受け取り引数を返そうとする特性を利用して、Requestのメソッドがなんなのかとパスがなんなのかをパターンマッチしているのです。

---

# ルーター

http4sのルーターは`Router`というobjectを使用して構築します。
実装自体は以下のように行います。

```scala
val router: HttpRoutes[IO] = Router(
  "/"    -> helloWorldService,
  "/api" -> apiService
)
```

---

# ルーター

Play Frameworkのroutesファイルを思い出してください。

Play Frameworkはベースに`routes`ファイルを定義して、ある特定のパス配下のルーティングを別のファイルに切り出して実装を行うことができましたよね？

`routes`と`sub.api.routes`ファイルがある場合

```conf
# routesファイルに定義
-> /api sub.api.Routes
```

Routerの実装は、http4におけるこのPlay Frameworkと同じような実装だと思ってください。

---

# ルーター

Routerを構築したはずなのに、戻り値の型がサービスと同じになっているのに気づいたでしょうか？
なぜRouterを構築しているのに型は変わらないのか？

実装を見て確認して見ましょう。

```scala
def apply[F[_]: Monad](mappings: (String, HttpRoutes[F])*): HttpRoutes[F] =
  define(mappings: _*)(HttpRoutes.empty[F])
```

`apply`メソッドは`define`メソッドを呼んでいるのでそちらも見てみましょう。

---

# ルーター

単純に何をやっているかというと文字列で指定したパスの情報が空でなかった場合に、受け取ったRequestのパス情報の最初が指定されたパスの文字列と一致していれば後続の処理を行い、一致していない場合はdefault(ここではempty)の処理を行うようになっています。

```scala
def define[F[_]: Monad](
  mappings: (String, HttpRoutes[F])*
)(default: HttpRoutes[F]): HttpRoutes[F] =
  mappings.sortBy(_._1.length).foldLeft(default) { case (acc, (prefix, routes)) =>
    val prefixPath = Uri.Path.unsafeFromString(prefix)
    if (prefixPath.isEmpty) routes <+> acc
    else
      Kleisli { req =>
        if (req.pathInfo.startsWith(prefixPath))
          routes(translate(prefixPath)(req)).orElse(acc(req))
        else
          acc(req)
      }
  }
```

---

# つまり

つまりhttp4sにおいてRouterの実装は、特定の用途ごとにHttpRoutesをマッピングする処理を行うということです。

---

# サーバー

最初に挙げたように、http4sは様々なバックエンドをサポートしています。
今回はその中のEmberServerを使用してみます。

サーバーの構築は各種Builderを使用して実装を行います。

```scala
import org.http4s.ember.server.EmberServerBuilder
```

---

# サーバー

以下が最小のサーバー構築になります。

```scala
EmberServerBuilder
  .default[IO]
  .withHttpApp(router.orNotFound)
  .build
```

typelevel系のライブラリは`EmberServerBuilder.default[F]`のように型パラメータでエフェクトシステムを切り替えることができるようになっています。

また、`EmberServerBuilder.default[F].build`は`Resource`型を返すので開発者は明示的に`use` メソッドを呼ぶ必要があります。
`Resource`はリソースの生成・破棄を自動で行う機能です。

---

# サーバー

なぜ`default`なのか？
これはEmberServerBuilderのパラメーターが`private`になっており、`default`でインスタンスの生成をデフォルト引数で行っているためです。

```scala
final class EmberServerBuilder[F[_]: Async: Network] private (...)

...

def default[F[_]: Async: Network]: EmberServerBuilder[F] =
  new EmberServerBuilder[F](...)
```

---

# サーバー

EmberServerBuilderには`copy`メソッドが用意されており、各パラメーターの更新はこの`copy`メソッドを使用したメソッドを使用して更新を行っていきます。

今回はHttpAppの更新を行うために、`withHttpApp`メソッドを使用してパラメーターの更新を行いました。

```scala
def withHttpApp(httpApp: HttpApp[F]): EmberServerBuilder[F] = copy(httpApp = _ => httpApp)
```

---

# HttpApp?

HttpAppとは`Kleisli[F, Request[G], Response[G]]`の型エイリアスです。

HttpRoutesと似ていますね。では違いはなんでしょうか？
2つを見比べてみると、HttpRoutesはOptionTになっています。

```scala
// HttpRoutes
Kleisli[[T] =>> OptionT[F, T], Request[F], Response[F]]

// HttpApp
Kleisli[F, Request[G], Response[G]]
```

---

# HttpApp?

OptionTだと存在しないものがあった場合にNoneになってしまいます。
もしサーバーを起動していて、どのRequestにも一致しないリクエストが来た場合どうなるでしょうか？

おそらくNo Responseになってしまうので、一致するパスが存在しないのか、サーバーがそもそも起動していないのかわかりにくいですよね。

サーバーを起動した以上何かしらのResponseを返さないといけないので、サーバーで起動するHttpAppはOptionTでは無くなっているのです。

---

# HttpRoutes => HttpApp

HttpRoutesからHttpAppへの変換は以下のように行います。

```scala
router.orNotFound
```

これは受け取ったRequestに該当するものがない場合、NotFoundのレスポンスを返すというシンプルなものです。

---

# HttpRoutes => HttpApp

実装自体もシンプルなので、もし特別な処理が必要な場合は自身でカスタマイズして実装することもできます。

```scala
final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, *], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}
```

---

# サーバーの起動

```scala
import cats.effect.{ IO, Resource }
import cats.effect.unsafe.implicits.global

import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.server.{ Router, Server }
import org.http4s.ember.server.EmberServerBuilder

object HelloWorld:

  val helloWorldService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
  }

  val router: HttpRoutes[IO] = Router(
    "/" -> helloWorldService,
  )

  val server: Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHttpApp(router.orNotFound)
    .build

  def main(args: Array[String]): Unit =
    server.use(_ => IO.never).unsafeRunSync()
```

---

# サーバーの起動

サーバーの起動は、以下のような処理で行います。

serverは構築した時点では`Resource`になっているため、`use`を使用しています。
`IO.never`を使用しているのは、サーバーは起動したら停止するまで起動し続けてもらう必要があるため設定しています。(これがないと`run`コマンドで実行しても即時停止してしまいます)

最後にIOになったものを`unsafeRunSync`で実行しています。

```scala
def main(args: Array[String]): Unit =
  server.use(_ => IO.never).unsafeRunSync()
```

※ IOに関してはIOの章で説明するので、ここではそういうものかぐらいで大丈夫です。

---

# サーバーの起動

サーバーを起動した後、設定したパスにアクセスを行いレスポンスが正常に帰って来てるか確認してみましょう。

```shell
curl http://localhost:8080/hello/takapi
```

---

# IOAppでの実行

先ほど起動したサーバーをIOAppを使用して起動して見ましょう。

IOAppはプロセスを実行して、SIGTERMを受信したときに無限プロセスを中断し、サーバーを優雅にシャットダウンするためにJVMシャットダウンフックを追加してくれるものです。

IOを実行する時に必要な、Runtimeも内部で生成してくれます。

※ こちらもIOの章で説明します。

---

# IOAppでの実行

```scala
import cats.effect.*

import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.server.{ Router, Server }
import org.http4s.ember.server.EmberServerBuilder

object HelloWorldWithIOApp extends IOApp:

  val helloWorldService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
  }

  val router: HttpRoutes[IO] = Router(
    "/" -> helloWorldService,
  )

  val server: Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHttpApp(router.orNotFound)
    .build

  def run(args: List[String]): IO[ExitCode] =
    server
      .use(_ => IO.never)
      .as(ExitCode.Success)
```

---

# サーバーの起動

サーバーを起動した後、設定したパスにアクセスを行い同じようにレスポンスが正常に帰って来てるか確認してみましょう。

```shell
curl http://localhost:8080/hello/takapi
```

---

# まとめ

今まで触って来たPlay Frameworkと比べてみてどう感じましたか？

http4sは今まで触って来たものとは違い、かなり関数型だったかと思います。

---

# まとめ

ScalaMatsuri2023用のアンケートの結果を見てみると、以下のように今回触ったものが上位を占めている状態です。

1. Scala3
2. Software Design and Architecture
3. 関数型プログラミング一般や圏論
4. Effect System (Cats Effect / Monix / ZIO / eff etc.)

---

# まとめ

アンケート結果 (母数...)

![](../images/ScalaMatsuri2023.png)

---

# まとめ

今回はhttp4sを軽く紹介しましたが、次はCats EffectのEffect Systemを勉強していく予定です。

この夕学を通して、少しでもScalaを使ったEffect Systemや関数型プログラミングに興味を持っていただければなと思っています。
