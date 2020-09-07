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

import quasar.blobstore.azure._
import quasar.connector.{CompressionScheme, DataFormat}, DataFormat._

import argonaut._, Argonaut._

object json {
  implicit val credentialsCodec: CodecJson[AzureCredentials] =
    CodecJson({
      case AzureCredentials.SharedKey(AccountName(an), AccountKey(ak)) =>
        argonaut.Json.obj(
          "auth" := "sharedKey",
          "accountName" := an,
          "accountKey" := ak)
      case AzureCredentials.ActiveDirectory(ClientId(cid), TenantId(tid), ClientSecret(cs)) =>
        argonaut.Json.obj(
          "auth" := "activeDirectory",
          "clientId" := cid,
          "tenantId" := tid,
          "clientSecret" := cs)
    }, c => for {
      auth <- c.get[String]("auth") ||| DecodeResult.ok("sharedKey")
      credentials <- auth match {
        case "sharedKey" => for {
          an <- c.get[String]("accountName")
          ak <- c.get[String]("accountKey")
        } yield AzureCredentials.SharedKey(AccountName(an), AccountKey(ak))

        case "activeDirectory" => for {
          cid <- c.get[String]("clientId")
          tid <- c.get[String]("tenantId")
          cs <- c.get[String]("clientSecret")
        } yield AzureCredentials.ActiveDirectory(ClientId(cid), TenantId(tid), ClientSecret(cs))

        case _ => DecodeResult.fail("auth must be 'sharedKey' or 'activeDirectory", c.history)
      }
    } yield credentials)

  implicit val decodeContainerName: DecodeJson[ContainerName] = jdecode1(ContainerName(_))
  implicit val decodeStorageUrl: DecodeJson[StorageUrl] = jdecode1(StorageUrl(_))
  implicit val decodeAccountName: DecodeJson[AccountName] = jdecode1(AccountName(_))
  implicit val decodeAccountKey: DecodeJson[AccountKey] = jdecode1(AccountKey(_))
  implicit val decodeMaxQueueSize: DecodeJson[MaxQueueSize] =
    DecodeJson(c =>
      DecodeJson.of[Int].decode(c).flatMap(i =>
        MaxQueueSize(i).fold(
          DecodeResult.fail[MaxQueueSize](s"Failed to decode max queue size: $i", c.history))(
          DecodeResult.ok(_))))

  implicit val encodeContainerName: EncodeJson[ContainerName] = jencode1(_.value)
  implicit val encodeStorageUrl: EncodeJson[StorageUrl] = jencode1(_.value)
  implicit val encodeAccountName: EncodeJson[AccountName] = jencode1(_.value)
  implicit val encodeAccountKey: EncodeJson[AccountKey] = jencode1(_.value)
  implicit val encodeMaxQueueSize: EncodeJson[MaxQueueSize] = jencode1(_.value)

  val legacyDecodeFlatFormat: DecodeJson[DataFormat] = DecodeJson { c => c.as[String].flatMap {
    case "json" => DecodeResult.ok(DataFormat.json)
    case "ldjson" => DecodeResult.ok(DataFormat.ldjson)
    case other => DecodeResult.fail(s"Unrecognized parsing format: $other", c.history)
  }}

  val legacyDecodeDataFormat: DecodeJson[DataFormat] = DecodeJson( c => for {
    parsing <- (c --\ "resourceType").as(legacyDecodeFlatFormat)
    compressionScheme <- (c --\ "compressionScheme").as[Option[CompressionScheme]]
  } yield compressionScheme match {
    case None => parsing
    case Some(_) => DataFormat.gzipped(parsing)
  })

  implicit val codecConfig: CodecJson[AzureConfig] = CodecJson({ (c: AzureConfig) =>
    ("container" := c.containerName) ->:
    ("credentials" := c.credentials) ->:
    ("storageUrl" := c.storageUrl) ->:
    ("maxQueueSize" := c.maxQueueSize) ->:
    c.format.asJson
  }, (c => for {
    format <- c.as[DataFormat] ||| c.as(legacyDecodeDataFormat)
    container <- (c --\ "container").as[ContainerName]
    credentials <- ((c --\ "credentials").as[Option[AzureCredentials]])
    storageUrl <- (c --\ "storageUrl").as[StorageUrl]
    maxQueueSize <- (c --\ "maxQueueSize").as[Option[MaxQueueSize]]
  } yield AzureConfig(container, credentials, storageUrl, maxQueueSize, format)))

}
