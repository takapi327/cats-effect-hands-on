ThisBuild / version      := "1.0.0"
ThisBuild / organization := "com.github.takapi327"
ThisBuild / scalaVersion := "3.2.0"
ThisBuild / startYear    := Some(2022)

lazy val root = (project in file("."))
  .settings(
    name := "cats-effect-hands-on",
    run / fork := true,
  )
