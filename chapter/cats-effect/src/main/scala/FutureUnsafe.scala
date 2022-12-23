
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FutureUnsafe extends App:

  val print = Future(println("Hello World!"))
  val twice = print.flatMap(_ => print)
