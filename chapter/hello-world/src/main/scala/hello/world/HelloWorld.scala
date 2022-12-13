package hello.world

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
