import scala.collection.Seq

performMavenCentralSync in ThisBuild := false   // basically just ignores all the sonatype sync parts of things

publishAsOSSProject in ThisBuild := true

homepage in ThisBuild := Some(url("https://github.com/slamdata/quasar-blobstore"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/slamdata/quasar-blobstore"),
  "scm:git@github.com:slamdata/quasar-blobstore.git"))

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(core)

val quasarVersion = IO.read(file("./quasar-version")).trim

val argonautRefinedVersion = "1.2.0-M8"
val azureVersion = "10.1.0"
val catsEffectVersion = "1.0.0"
val fs2Version = "1.0.0"
val nettyVersion = "4.1.28.Final"
val rxjavaVersion = "2.2.2"
val shimsVersion = "1.2.1"
val slf4jVersion = "1.7.25"
val specsVersion = "4.1.2"

lazy val core = project
  .in(file("datasource"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"))
  .settings(
    name := "quasar-blobstore",

    datasourceName := "azure",

    datasourceQuasarVersion := quasarVersion,

    datasourceModuleFqcn := "quasar.physical.blobstore.azure.AzureDatasourceModule$",

    /** Specify managed dependencies here instead of with `libraryDependencies`.
      * Do not include quasar libs, they will be included based on the value of
      * `datasourceQuasarVersion`.
      */
    datasourceDependencies ++= Seq(
      "com.codecommit"             %% "shims"                      % shimsVersion,
      "com.github.alexarchambault" %% "argonaut-refined_6.2"       % argonautRefinedVersion,
      "com.microsoft.azure"        %  "azure-storage-blob"         % azureVersion,
      // netty-all isn't strictly necessary but takes advantage of native libs.
      // Azure doesn't pull in libs like netty-transport-native-kqueue,
      // netty-transport-native-unix-common and netty-transport-native-epoll.
      // Keep nettyVersion in sync with the version that Azure pulls in.
      "io.netty"             %  "netty-all"                  % nettyVersion,
      "io.reactivex.rxjava2" %  "rxjava"                     % rxjavaVersion,
      "org.typelevel"        %% "cats-effect"                % catsEffectVersion,
      "com.slamdata"         %% "quasar-foundation-internal" % quasarVersion % Test classifier "tests",
      "org.slf4j"            %  "slf4j-log4j12"              % slf4jVersion % Test,
      "org.specs2"           %% "specs2-core"                % specsVersion % Test,
      "org.specs2"           %% "specs2-scalaz"              % specsVersion % Test,
      "org.specs2"           %% "specs2-scalacheck"          % specsVersion % Test
    ))
  .enablePlugins(AutomateHeaderPlugin, DatasourcePlugin)
