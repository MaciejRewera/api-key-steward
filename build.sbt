ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

name := "api-key-steward"

Global / onChangedBuildSource := ReloadOnSourceChanges

val Http4sVersion = "0.23.15"
val circeVersion = "0.14.5"
val tapirVersion = "1.4.0"
val DoobieVersion = "1.0.0-M5"
val pureConfigVersion = "0.17.4"

libraryDependencies ++= Seq(

  //Cats
  "org.typelevel" %% "cats-core" % "2.9.0",
  "org.typelevel" %% "cats-effect" % "3.5.1",
  "co.fs2" %% "fs2-core" % "3.9.1",

  // Http
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,

  // JSON
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-literal" % circeVersion,

  // Tapir
  "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server" % tapirVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.3.2",
  "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-http4s" % "0.19.0-M4",
  "com.softwaremill.sttp.tapir"   %% "tapir-cats" % tapirVersion,

  // Database
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
  "com.zaxxer" % "HikariCP" % "5.0.1",
  "org.postgresql" % "postgresql" % "42.6.0",
  "org.apache.commons" % "commons-lang3" % "3.13.0",
  "com.github.geirolz" %% "fly4s-core" % "0.0.19",

  // Config
  "com.github.pureconfig" %% "pureconfig" % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-http4s" % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-ip4s" % pureConfigVersion,

  // Logger
  "ch.qos.logback" % "logback-classic" % "1.4.7",

  // JWT
  "com.github.jwt-scala" %% "jwt-core" % "9.4.5",

  //Test
  "org.scalatest" %% "scalatest" % "3.2.16" % Test,
  "org.mockito" %% "mockito-scala-scalatest" % "1.17.29" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "api-key-steward",
    scalafmtOnCompile := true
  )

lazy val it = (project in file("integration-tests"))
  .dependsOn(root)
  .settings(
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-scalatest" % DoobieVersion % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test,
      "com.github.tomakehurst" % "wiremock" % "3.0.1" % Test
    )
  )
