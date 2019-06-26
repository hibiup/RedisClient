organization := "com.example"
version := "0.1"

lazy val scalaLibraries = new {
    val scalaTestVersion = "3.0.5"
    val logBackVersion = "1.2.3"
    val scalaLoggingVersion = "3.9.2"
    val rediscalaVersion = "1.9.0"
    //val catsVersion = "1.6.0"
    //val catsEffectVersion = "1.2.0"

    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    //val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
    val logback = "ch.qos.logback" % "logback-classic" % logBackVersion
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
    val rediscala = "com.github.etaty" %% "rediscala" % rediscalaVersion
    //val catsCore = "org.typelevel" %% "cats-core" % catsVersion
    //val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectVersion
    //val catsFree = "org.typelevel" %% "cats-free" % catsVersion
}

lazy val scalaDependencies = Seq(
    scalaLibraries.scalaTest,
    //scalaLibraries.scalaCheck,
    scalaLibraries.logback,
    scalaLibraries.scalaLogging,
    scalaLibraries.rediscala
    //scalaLibraries.catsCore,
    //scalaLibraries.catsFree,
    //scalaLibraries.catsEffect,
)

lazy val ScalaCaseStudy = (project in file("."))
    .settings(
        name := "RedisClientExamples",
        scalaVersion := "2.12.8",
        libraryDependencies ++= scalaDependencies,
        addCompilerPlugin("org.scalameta" %% "paradise" % "3.0.0-M11" cross CrossVersion.full),
        addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),
        scalacOptions ++= Seq(
            "-Xplugin-require:macroparadise",
            "-language:higherKinds",
            "-deprecation",
            "-encoding", "UTF-8",
            "-Ypartial-unification",
            "-feature",
            "-language:_"
        )
    )