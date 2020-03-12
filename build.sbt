import scala.collection.Seq

ThisBuild / scalaVersion := "2.12.10"

publishAsOSSProject in ThisBuild := true

homepage in ThisBuild := Some(url("https://github.com/slamdata/quasar-datasource-azure"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/slamdata/quasar-datasource-azure"),
  "scm:git@github.com:slamdata/quasar-datasource-azure.git"))

// Include to also publish a project's tests
lazy val publishTestsSettings = Seq(
  publishArtifact in (Test, packageBin) := true)

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(core, azure)

val argonautRefinedVersion = "1.2.0-M11"

val refinedVersion = "0.9.9"
val nettyVersion = "4.1.44.Final"
val slf4jVersion = "1.7.25"
val specsVersion = "4.8.3"

lazy val core = project
  .in(file("core"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
  .settings(publishTestsSettings)
  .settings(
    name := "quasar-datasource-blobstore-core",
    libraryDependencies ++= Seq(
      "com.slamdata" %% "async-blobstore-core" % managedVersions.value("slamdata-async-blobstore"),
      "com.slamdata" %% "quasar-connector" % managedVersions.value("slamdata-quasar"),
      "com.slamdata" %% "quasar-connector" % managedVersions.value("slamdata-quasar") % Test classifier "tests",
      "com.slamdata" %% "quasar-foundation" % managedVersions.value("slamdata-quasar") % Test classifier "tests",
      "org.specs2" %% "specs2-core" % specsVersion % Test,
      "org.specs2" %% "specs2-scalaz" % specsVersion % Test,
      "org.specs2" %% "specs2-scalacheck" % specsVersion % Test))

lazy val azure = project
  .in(file("azure"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
  .settings(
    name := "quasar-datasource-azure",

    quasarPluginName := "azure",

    quasarPluginQuasarVersion := managedVersions.value("slamdata-quasar"),

    quasarPluginDatasourceFqcn := Some("quasar.physical.blobstore.azure.AzureDatasourceModule$"),

    /** Specify managed dependencies here instead of with `libraryDependencies`.
      * Do not include quasar libs, they will be included based on the value of
      * `datasourceQuasarVersion`.
      */
    quasarPluginDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-refined_6.2" % argonautRefinedVersion,
      "com.slamdata" %% "async-blobstore-azure" % managedVersions.value("slamdata-async-blobstore") excludeAll(ExclusionRule(organization = "io.netty")),
      "io.netty" % "netty-all" % nettyVersion,
      "eu.timepit" %% "refined-scalacheck" % refinedVersion,
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test))

  .enablePlugins(AutomateHeaderPlugin, QuasarPlugin)
