import scala.collection.Seq

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

val quasarVersion = IO.read(file("./quasar-version")).trim

val argonautRefinedVersion = "1.2.0-M8"
val asyncBlobstoreVersion = "0.1.0"

val refinedVersion = "0.8.5"
val slf4jVersion = "1.7.25"
val specsVersion = "4.6.0"

lazy val core = project
  .in(file("core"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
  .settings(publishTestsSettings)
  .settings(
    name := "quasar-datasource-blobstore-core",
    libraryDependencies ++= Seq(
      "com.slamdata" %% "async-blobstore-core" % asyncBlobstoreVersion,
      "com.slamdata" %% "quasar-connector" % quasarVersion,
      "com.slamdata" %% "quasar-connector" % quasarVersion % Test classifier "tests",
      "com.slamdata" %% "quasar-foundation" % quasarVersion % Test classifier "tests",
      "org.specs2" %% "specs2-core" % specsVersion % Test,
      "org.specs2" %% "specs2-scalaz" % specsVersion % Test,
      "org.specs2" %% "specs2-scalacheck" % specsVersion % Test))

lazy val azure = project
  .in(file("azure"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
  .settings(
    name := "quasar-datasource-azure",

    datasourceName := "azure",

    datasourceQuasarVersion := quasarVersion,

    datasourceModuleFqcn := "quasar.physical.blobstore.azure.AzureDatasourceModule$",

    /** Specify managed dependencies here instead of with `libraryDependencies`.
      * Do not include quasar libs, they will be included based on the value of
      * `datasourceQuasarVersion`.
      */
    datasourceDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-refined_6.2" % argonautRefinedVersion,
      "com.slamdata" %% "async-blobstore-azure" % asyncBlobstoreVersion,
      "eu.timepit" %% "refined-scalacheck" % refinedVersion,
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test))

  .enablePlugins(AutomateHeaderPlugin, DatasourcePlugin)
