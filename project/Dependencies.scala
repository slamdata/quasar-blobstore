package quasar.blobstore.project

import scala.Boolean
import scala.collection.Seq

import sbt._

object Dependencies {

  private val azureVersion = "10.1.0"
  private val rxjavaVersion = "2.2.2"
  private val catsEffectVersion = "1.0.0"
  private val fs2Version = "1.0.0"
  private val quasarVersion = IO.read(file("./quasar-version")).trim
  private val qdataVersion = IO.read(file("./qdata-version")).trim
  private val shimsVersion = "1.2.1"
  private val specsVersion = "4.1.2"

  // direct as well as transitive deps need to be in sync with quasar's deps
  def datasourceCore = Seq(
    "com.codecommit"         %% "shims"               % shimsVersion,
    "com.microsoft.azure"    %  "azure-storage-blob"  % azureVersion,
    "com.slamdata"           %% "qdata-json"          % qdataVersion,
    "io.reactivex.rxjava2"   %  "rxjava"              % rxjavaVersion,
    "org.typelevel"          %% "cats-effect"         % catsEffectVersion,
    "org.specs2"             %% "specs2-core"         % specsVersion % Test,
    "org.specs2"             %% "specs2-scalaz"       % specsVersion % Test,
    "org.specs2"             %% "specs2-scalacheck"   % specsVersion % Test,
  )

  // we need to separate quasar out from the datasource dependencies,
  // to keep from packaging it and its dependencies. TODO: we should
  // do this in the assembly routine.
  def datasource = datasourceCore ++ Seq(
    "com.slamdata" %% "quasar-api-internal"        % quasarVersion,
    "com.slamdata" %% "quasar-api-internal"        % quasarVersion % Test classifier "tests",
    "com.slamdata" %% "quasar-foundation-internal" % quasarVersion,
    "com.slamdata" %% "quasar-foundation-internal" % quasarVersion % Test classifier "tests",
    "com.slamdata" %% "quasar-connector-internal"  % quasarVersion,
    "com.slamdata" %% "quasar-connector-internal"  % quasarVersion % Test classifier "tests",
  )
}
