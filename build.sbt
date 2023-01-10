name := "fb_adviser"

ThisBuild / organization := "yakushev"
ThisBuild / version      := "0.1.5"
ThisBuild / scalaVersion := "2.12.15"

val Version_zio  = "2.0.0-RC6" // "2.0.0"
val Version_bot4s = "5.6.0"
val CirceVers = "0.14.2"
//https://sttp.softwaremill.com/en/latest/backends/zio.html
val VarsionZioSttp = "3.6.2"
val VersionPg = "42.4.0"
/*
val VersionZioConfig = "3.0.1"
val VersionZioConfigTs = "3.0.1"
val VersionZioConfigMag = "3.0.1"
*/
val VersionBot4sTeleg = "5.6.0"

// PROJECTS
lazy val global = project
  .in(file("."))
  .settings(commonSettings)
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    adviser
  )

lazy val adviser = (project in file("adviser"))
  .settings(
    Compile / mainClass        := Some("app.MainApp"),
    assembly / assemblyJarName := "fb_adviser.jar",
    name := "adviser",
    commonSettings,
    libraryDependencies ++= commonDependencies
  )

lazy val dependencies =
  new {
    val zio = "dev.zio" %% "zio" % Version_zio
    val zio_logging = "dev.zio" %% "zio-logging" % Version_zio
    val zio_test = "dev.zio" %% "zio-test" % Version_zio
    val zio_test_sbt = "dev.zio" %% "zio-test-sbt" % Version_zio

    val zio_sttp = "com.softwaremill.sttp.client3" %% "zio" % VarsionZioSttp
    val zio_sttp_async = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % VarsionZioSttp
    val zio_sttp_circe = "com.softwaremill.sttp.client3" %% "circe" % VarsionZioSttp // try zio-json

    //json support in client3 https://sttp.softwaremill.com/en/latest/json.html
    //val zio_config = "dev.zio" %% "zio-config" % VersionZioConfig
    //val zio_config_typesafe = "dev.zio" %% "zio-config-typesafe" % VersionZioConfigTs
    //val zio_config_magnolia = "dev.zio" %% "zio-config-magnolia" % VersionZioConfigMag

    val bot4s_core = "com.bot4s" %% "telegram-core" %  Version_bot4s
    val bot4s_akka = "com.bot4s" %% "telegram-akka" %  Version_bot4s

    //val lb = List(logback)
    val zioDep = List(zio, zio_logging, zio_test, zio_test_sbt,
      zio_sttp, zio_sttp_async,
      zio_sttp_circe//, zio_config, zio_config_typesafe, zio_config_magnolia
    )
    val bot4Dep = List(bot4s_core, bot4s_akka)

    val circe_libs = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-literal"
    ).map(_ % CirceVers)

    val circe_optics = Seq("io.circe" %% "circe-optics" % "0.14.1")

    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    val pg_driver = Seq("org.postgresql" % "postgresql" % VersionPg)

    val telegCore = "com.bot4s" %% "telegram-core" %  VersionBot4sTeleg // "HEAD_SNAPSHOT",
    val telegAkka = "com.bot4s" %% "telegram-akka" %  VersionBot4sTeleg // HEAD_SNAPSHOT",

    val bot4slibs = Seq(telegCore,telegAkka)

  }

val commonDependencies = {
  dependencies.zioDep ++
    dependencies.bot4Dep ++
    dependencies.circe_libs ++
    dependencies.circe_optics ++
    dependencies.pg_driver ++
    dependencies.bot4slibs
}

  lazy val compilerOptions = Seq(
          "-deprecation",
          "-encoding", "utf-8",
          "-explaintypes",
          "-feature",
          "-unchecked",
          "-language:postfixOps",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-Xcheckinit",
          "-Xfatal-warnings",
          "-Ywarn-unused:params,-implicits"
  )

  lazy val commonSettings = Seq(
    scalacOptions ++= compilerOptions,
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("public"),
      Resolver.sonatypeRepo("releases"),
      Resolver.DefaultMavenRepository,
      Resolver.mavenLocal,
      Resolver.bintrayRepo("websudos", "oss-releases")
    )
  )

  addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)

  adviser / assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("/module-info.class") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*)         => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }