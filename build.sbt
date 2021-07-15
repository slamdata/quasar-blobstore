import scala.collection.Seq

ThisBuild / scalaVersion := "2.12.11"

publishAsOSSProject in ThisBuild := true

ThisBuild / githubRepository := "quasar-datasource-azure"

homepage in ThisBuild := Some(url("https://github.com/precog/quasar-datasource-azure"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/precog/quasar-datasource-azure"),
  "scm:git@github.com:precog/quasar-datasource-azure.git"))

// Include to also publish a project's tests
lazy val publishTestsSettings = Seq(
  publishArtifact in (Test, packageBin) := true)

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(azure)

val slf4jVersion = "1.7.25"
val specsVersion = "4.9.2"

lazy val azure = project
  .in(file("azure"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
  .settings(
    name := "quasar-datasource-azure",

    quasarPluginName := "azure",

    quasarPluginQuasarVersion := managedVersions.value("precog-quasar"),

    quasarPluginDatasourceFqcn := Some("quasar.physical.blobstore.azure.AzureDatasourceModule$"),

    /** Specify managed dependencies here instead of with `libraryDependencies`.
      * Do not include quasar libs, they will be included based on the value of
      * `datasourceQuasarVersion`.
      */
    quasarPluginDependencies ++= Seq(
      "com.precog" %% "async-blobstore-azure" % managedVersions.value("precog-async-blobstore"),
      "com.precog" %% "quasar-lib-blobstore" % managedVersions.value("precog-quasar-lib-blobstore") % "compile->compile;test->test",
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test))
  .enablePlugins(QuasarPlugin)
  .evictToLocal("QUASAR_PATH", "connector", true)
  .evictToLocal("QUASAR_PATH", "api", true)
  .evictToLocal("ASYNC_BLOBSTORE_PATH", "gcs", true)
  .evictToLocal("BLOBSTORE_PATH", "core", true)
