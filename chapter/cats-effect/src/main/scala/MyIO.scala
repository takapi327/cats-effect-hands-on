
case class MyIO[A](unsafeRun: () => A)

object MyIO:
  def putStr(s: => String): MyIO[Unit] =
    MyIO(() => println(s))

object Printing extends App:
  val hello = MyIO.putStr("hello!")
  hello.unsafeRun()
