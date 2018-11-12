/*
 * Copyright 2014–2018 SlamData Inc.
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

import argonaut._, Argonaut._
import eu.timepit.refined.auto._

import org.specs2.mutable.Specification

class AzureDatasourceModuleSpec extends Specification {

  private def credToJson(cred: AzureCredentials): Json =
    Json.obj(
      "accountName" -> Json.jString(cred.accountName.value),
      "accountKey" -> Json.jString(cred.accountKey.value))

  private def cfgToJson(cfg: AzureConfig): Json =
    Json.obj(
      "container" -> Json.jString(cfg.containerName.value),
      "credentials" -> cfg.credentials.fold(jNull)(credToJson),
      "storageUrl" -> Json.jString(cfg.storageUrl.value),
      "maxQueueSize" -> cfg.maxQueueSize.fold(jNull)(qs => Json.jNumber(qs.value.value)))

  "sanitize config" >> {

    "redacts config with credentials" >> {
      val cfg = AzureConfig(
        ContainerName("mycontainer"),
        Some(AzureCredentials(AccountName("myname"), AccountKey("mykey"))),
        Azure.mkStdStorageUrl(AccountName("myaccount")),
        Some(MaxQueueSize(10)))

      AzureDatasourceModule.sanitizeConfig(cfgToJson(cfg)) must_===
        cfgToJson(AzureConfig(
          ContainerName("mycontainer"),
          Some(AzureCredentials(AccountName("<REDACTED>"), AccountKey("<REDACTED>"))),
          Azure.mkStdStorageUrl(AccountName("myaccount")),
          Some(MaxQueueSize(10))))
    }

    "does not change config without credentials" >> {
      val cfg = cfgToJson(AzureConfig(
        ContainerName("mycontainer"),
        None,
        Azure.mkStdStorageUrl(AccountName("myaccount")),
        Some(MaxQueueSize(10))))

      AzureDatasourceModule.sanitizeConfig(cfg) must_=== cfg
    }
  }

}
