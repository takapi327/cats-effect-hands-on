package hello.world

import cats.effect.*

import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.server.{ Router, Server }
import org.http4s.ember.server.EmberServerBuilder


trait TypeLambda[F[_]]
trait TypeLambda1[F[_, _]]

type MapA[A] = Map[Int, A]

type F1 = TypeLambda[Option] // OK
type F2 = TypeLambda[List]   // OK
type F3 = TypeLambda[[A] =>> Map[Int, A]]    // !!
//type F3 = TypeLambda[MapA]    // !!

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
