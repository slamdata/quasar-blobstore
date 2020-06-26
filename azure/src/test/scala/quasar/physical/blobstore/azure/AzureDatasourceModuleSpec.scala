/*
 * Copyright 2020 Precog Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.physical.blobstore.azure

import slamdata.Predef._

import quasar.{RateLimiter, NoopRateLimitUpdater}
import quasar.connector.datasource.Reconfiguration
import quasar.api.datasource.DatasourceError, DatasourceError._
import quasar.connector.{ByteStore, DataFormat}
import quasar.blobstore.azure._

import java.util.UUID
import scala.concurrent.ExecutionContext

import argonaut._, Argonaut._
import cats.effect.{ContextShift, IO, Timer}
import cats.kernel.instances.uuid._
import cats.instances.either._
import cats.syntax.functor._
import org.specs2.mutable.Specification
import scalaz.NonEmptyList

class AzureDatasourceModuleSpec extends Specification {
  import AzureDatasourceSpec._

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  private def credToJson(cred: AzureCredentials.SharedKey): Json =
    Json.obj(
      "accountName" -> Json.jString(cred.accountName.value),
      "accountKey" -> Json.jString(cred.accountKey.value))

  private def init(j: Json) =
    RateLimiter[IO, UUID](1.0, IO.delay(UUID.randomUUID()), NoopRateLimitUpdater[IO, UUID]).flatMap(rl =>
      AzureDatasourceModule.lightweightDatasource[IO, UUID](j, rl, ByteStore.void[IO])
        .use(r => IO.pure(r.void)))
        .unsafeRunSync()

  private def cfgToJson(cfg: AzureConfig, stripNulls: Boolean = true): Json = {
    val js =
      ("container" := Json.jString(cfg.containerName.value)) ->:
      ("credentials" := cfg.credentials.fold(jNull)(credToJson)) ->:
      ("storageUrl" := Json.jString(cfg.storageUrl.value)) ->:
      ("maxQueueSize" := cfg.maxQueueSize.fold(jNull)(qs => Json.jNumber(qs.value))) ->:
      cfg.format.asJson

    if (stripNulls)
      js.withObject(j => JsonObject.fromIterable(j.toList.filter(!_._2.isNull)))
    else
      js
  }

  "datasource init" >> {
    "succeeds when correct cfg without credentials" >> {
      init(cfgToJson(Fixtures.PublicConfig)) must beRight
    }

    "fails with access denied when invalid credentials" >> {
      init(cfgToJson(Fixtures.PublicConfig.copy(credentials = Some(Fixtures.InvalidCredentials)))) must beLike {
        case Left(DatasourceError.AccessDenied(_, _, _)) => ok
      }
    }

    "fails with invalid config when invalid storage url" >> {
      init(cfgToJson(Fixtures.PublicConfig.copy(storageUrl = StorageUrl("invalid")))) must beLike {
        case Left(DatasourceError.InvalidConfiguration(_, _, _)) => ok
      }
    }

    "fails with invalid config when non-existing storage url" >> {
      init(cfgToJson(Fixtures.PublicConfig.copy(storageUrl = Azure.mkStdStorageUrl(AccountName("nonexisting"))))) must beLike {
        case Left(DatasourceError.InvalidConfiguration(_, _, _)) => ok
      }
    }

    "fails with invalid config when non-existing container " >> {
      init(cfgToJson(Fixtures.PublicConfig.copy(containerName = ContainerName("nonexisting")))) must beLike {
        case Left(DatasourceError.InvalidConfiguration(_, _, _)) => ok
      }
    }
  }

  "sanitize config" >> {

    "redacts config with credentials" >> {
      val cfg = AzureConfig(
        ContainerName("mycontainer"),
        Some(AzureCredentials.SharedKey(AccountName("myname"), AccountKey("mykey"))),
        Azure.mkStdStorageUrl(AccountName("myaccount")),
        MaxQueueSize(10),
        DataFormat.json)

      AzureDatasourceModule.sanitizeConfig(cfgToJson(cfg)) must_===
        cfgToJson(AzureConfig(
          ContainerName("mycontainer"),
          Some(AzureCredentials.SharedKey(AccountName("<REDACTED>"), AccountKey("<REDACTED>"))),
          Azure.mkStdStorageUrl(AccountName("myaccount")),
          MaxQueueSize(10),
          DataFormat.json))
    }

    "migrate config without credentials" >> {
      val cfg = Json.obj(
        "container" -> jString("mycontainer"),
        "storageUrl" -> jString("url"),
        "maxQueueSize" -> jNumber(10),
        "resourceType" -> jString("ldjson"))

      val expected = Json.obj(
        "credentials" -> jNull,
        "container" -> jString("mycontainer"),
        "storageUrl" -> jString("url"),
        "maxQueueSize" -> jNumber(10),
        "format" -> Json.obj(
          "type" -> jString("json"),
          "variant" -> jString("line-delimited"),
          "precise" -> jBool(false)))

      AzureDatasourceModule.sanitizeConfig(cfg) must_=== expected
    }

    "migrate config with null credentials" >> {
      val cfg = Json.obj(
        "credentials" -> jNull,
        "container" -> jString("mycontainer"),
        "storageUrl" -> jString("url"),
        "maxQueueSize" -> jNumber(10),
        "resourceType" -> jString("ldjson"))

      val expected = Json.obj(
        "credentials" -> jNull,
        "container" -> jString("mycontainer"),
        "storageUrl" -> jString("url"),
        "maxQueueSize" -> jNumber(10),
        "format" -> Json.obj(
          "type" -> jString("json"),
          "variant" -> jString("line-delimited"),
          "precise" -> jBool(false)))

      AzureDatasourceModule.sanitizeConfig(cfg) must_=== expected
    }
  }

  "reconfiguration" >> {

    val origWithCreds = Json.obj(
      "credentials" -> Json.obj(
        "accountName" -> jString("myaccount"),
        "accountKey" -> jString("mykey")),
      "container" -> jString("mycontainer"),
      "storageUrl" -> jString("url"),
      "maxQueueSize" -> jNumber(10),
      "format" -> Json.obj(
        "type" -> jString("json"),
        "variant" -> jString("line-delimited"),
        "precise" -> jBool(false)))

    val origNoCreds = Json.obj(
      "container" -> jString("mycontainer"),
      "storageUrl" -> jString("publicurl"),
      "maxQueueSize" -> jNumber(10),
      "format" -> Json.obj(
        "type" -> jString("json"),
        "variant" -> jString("line-delimited"),
        "precise" -> jBool(false)))

    val patch = Json.obj(
      "credentials" -> jNull,
      "container" -> jString("patched mycontainer"),
      "storageUrl" -> jString("patched url"),
      "maxQueueSize" -> jNumber(42),
      "format" -> Json.obj(
        "type" -> jString("json"),
        "variant" -> jString("array-wrapped"),
        "precise" -> jBool(false)))

    "replace non-sensitive part of config with patch when original has sensitive info" >> {

      val expected = Json.obj(
        "credentials" -> Json.obj(
          "accountName" -> jString("myaccount"),
          "accountKey" -> jString("mykey")),
        "container" -> jString("patched mycontainer"),
        "storageUrl" -> jString("patched url"),
        "maxQueueSize" -> jNumber(42),
        "format" -> Json.obj(
          "type" -> jString("json"),
          "variant" -> jString("array-wrapped"),
          "precise" -> jBool(false)))

      AzureDatasourceModule.reconfigure(origWithCreds, patch) must beRight((Reconfiguration.Reset, expected))
    }

    "replace config with patch when original has no sensitive info" >> {

      AzureDatasourceModule.reconfigure(origNoCreds, patch) must beRight((Reconfiguration.Reset, patch))
    }

    "fails with invalid configuration error if patch has sensitive information" >> {

      AzureDatasourceModule.reconfigure(origNoCreds, origWithCreds) must beLeft(
        InvalidConfiguration(
          AzureDatasourceModule.kind,
          AzureDatasourceModule.sanitizeConfig(origWithCreds),
          NonEmptyList("Target configuration contains sensitive information.")))
    }

    "fails with malformed configuration error if patch is malformed" >> {

      AzureDatasourceModule.reconfigure(origNoCreds, Json.obj()) must beLeft(
        MalformedConfiguration(
          AzureDatasourceModule.kind,
          Json.obj(),
          "Target configuration in reconfiguration is malformed."))
    }

    "fails with malformed configuration error if orig is malformed" >> {

      AzureDatasourceModule.reconfigure(Json.obj(), patch) must beLeft(
        MalformedConfiguration(
          AzureDatasourceModule.kind,
          Json.obj(),
          "Source configuration in reconfiguration is malformed."))
    }
  }
}
