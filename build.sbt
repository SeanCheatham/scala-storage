lazy val commonSettings =
  Seq(
    organization := "com.seancheatham",
    scalaVersion := "2.11.8",
    libraryDependencies ++=
      Dependencies.typesafe ++
        Dependencies.test ++
        Dependencies.logging
  ) ++ Publish.settings

lazy val scalaStorage =
  project
    .in(file("."))
    .settings(commonSettings: _*)
    .settings(packagedArtifacts := Map.empty)
    .aggregate(core, firebase)

lazy val core =
  project
    .in(file("core"))
    .settings(commonSettings: _*)
    .settings(
      name := "storage-core",
      libraryDependencies ++= Dependencies.playJson.map(_ % Test)
    )

lazy val firebase =
  project
    .in(file("firebase"))
    .settings(commonSettings: _*)
    .settings(
      name := "storage-firebase",
      libraryDependencies ++=
        Dependencies.playJson ++
          Dependencies.firebase
    )
    .dependsOn(core % "compile->compile;test->test")