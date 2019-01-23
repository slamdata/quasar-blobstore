import scala.collection.Seq

publishAsOSSProject in ThisBuild := true

homepage in ThisBuild := Some(url("https://github.com/slamdata/quasar-datasource-azure"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/slamdata/quasar-datasource-azure"),
  "scm:git@github.com:slamdata/quasar-datasource-azure.git"))

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(core)

val quasarVersion = IO.read(file("./quasar-version")).trim

val argonautRefinedVersion = "1.2.0-M8"
val azureVersion = "10.3.0"
val catsEffectVersion = "1.1.0"
val fs2Version = "1.0.2"
val nettyVersion = "4.1.28.Final"
val refinedVersion = "0.8.5"
val rxjavaVersion = "2.2.2"
val shimsVersion = "1.7.0"
val slf4jVersion = "1.7.25"
val specsVersion = "4.3.3"

lazy val core = project
  .in(file("datasource"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"))
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
      "com.codecommit"             %% "shims"                      % shimsVersion,
      "com.github.alexarchambault" %% "argonaut-refined_6.2"       % argonautRefinedVersion,
      "com.microsoft.azure"        %  "azure-storage-blob"         % azureVersion,
      "eu.timepit"                 %% "refined-scalacheck"         % refinedVersion,
      // netty-all isn't strictly necessary but takes advantage of native libs.
      // Azure doesn't pull in libs like netty-transport-native-kqueue,
      // netty-transport-native-unix-common and netty-transport-native-epoll.
      // Keep nettyVersion in sync with the version that Azure pulls in.
      "io.netty"             %  "netty-all"         % nettyVersion,
      "io.reactivex.rxjava2" %  "rxjava"            % rxjavaVersion,
      "org.typelevel"        %% "cats-effect"       % catsEffectVersion,
      "com.slamdata"         %% "quasar-foundation" % quasarVersion % Test classifier "tests",
      "org.slf4j"            %  "slf4j-log4j12"     % slf4jVersion % Test,
      "org.specs2"           %% "specs2-core"       % specsVersion % Test,
      "org.specs2"           %% "specs2-scalaz"     % specsVersion % Test,
      "org.specs2"           %% "specs2-scalacheck" % specsVersion % Test
    ))
  .enablePlugins(AutomateHeaderPlugin, DatasourcePlugin)
