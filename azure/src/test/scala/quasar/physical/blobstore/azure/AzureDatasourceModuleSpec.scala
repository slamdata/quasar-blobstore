/*
 * Copyright 2014–2019 SlamData Inc.
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
import quasar.api.datasource.DatasourceError
import quasar.connector.DataFormat
import quasar.blobstore.azure._
import quasar.physical.blobstore.BlobstoreDatasource._

import scala.concurrent.ExecutionContext

import argonaut._, Argonaut._
import cats.effect.{ContextShift, IO, Timer}
import cats.instances.either._
import cats.syntax.functor._
import eu.timepit.refined.auto._
import org.specs2.mutable.Specification

class AzureDatasourceModuleSpec extends Specification {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  private def credToJson(cred: AzureCredentials.SharedKey): Json =
    Json.obj(
      "accountName" -> Json.jString(cred.accountName.value),
      "accountKey" -> Json.jString(cred.accountKey.value))

  private def init(j: Json) =
    AzureDatasourceModule.lightweightDatasource[IO](j)
      .use(r => IO.pure(r.void))
      .unsafeRunSync()

  private def cfgToJson(cfg: AzureConfig, stripNulls: Boolean = true): Json = {
    val js =
      ("container" := Json.jString(cfg.containerName.value)) ->:
      ("credentials" := cfg.credentials.fold(jNull)(credToJson)) ->:
      ("storageUrl" := Json.jString(cfg.storageUrl.value)) ->:
      ("maxQueueSize" := cfg.maxQueueSize.fold(jNull)(qs => Json.jNumber(qs.value.value))) ->:
      cfg.format.asJson

    if (stripNulls)
      js.withObject(j => JsonObject.fromTraversableOnce(j.toList.filter(!_._2.isNull)))
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
        Some(MaxQueueSize(10)),
        DataFormat.json)

      AzureDatasourceModule.sanitizeConfig(cfgToJson(cfg)) must_===
        cfgToJson(AzureConfig(
          ContainerName("mycontainer"),
          Some(AzureCredentials.SharedKey(AccountName("<REDACTED>"), AccountKey("<REDACTED>"))),
          Azure.mkStdStorageUrl(AccountName("myaccount")),
          Some(MaxQueueSize(10)),
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

}
