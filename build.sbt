
val http4sVersion = "0.23.16"

lazy val commonSettings = Seq(
  version      := "1.0.0",
  organization := "com.github.takapi327",
  scalaVersion := "3.2.0",
  startYear    := Some(2022),

  run / fork := true,

  scalacOptions ++= Seq(
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Ykind-projector:underscores"
  )
)

lazy val helloWorld = (project in file("chapter/hello-world"))
  .settings(name := "hello-world")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion
    )
  )

lazy val catsEffect = (project in file("chapter/cats-effect"))
  .settings(name := "cats-effect")
  .settings(commonSettings: _*)

lazy val doobie = (project in file("chapter/doobie"))
  .settings(name := "doobie")
  .settings(commonSettings: _*)

lazy val crud = (project in file("chapter/crud"))
  .settings(name := "crud")
  .settings(commonSettings: _*)

lazy val root = (project in file("."))
  .settings(name := "cats-effect-hands-on")
  .settings(commonSettings: _*)
  .aggregate(helloWorld, catsEffect, doobie, crud)
