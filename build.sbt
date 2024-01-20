ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "api-key-steward"
  )

lazy val it = (project in file("integration-tests"))
  .dependsOn(root)
  .settings(
    libraryDependencies ++= Seq()
  )
