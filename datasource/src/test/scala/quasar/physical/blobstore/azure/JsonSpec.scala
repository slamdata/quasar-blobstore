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
import quasar.blobstore.azure._, json._

import argonaut._, Argonaut._
import eu.timepit.refined.auto._
import org.specs2.mutable.Specification

class JsonSpec extends Specification {

  "json decoder" >> {

    "succeeds reading config with credentials" >> {
      val s =
        """
          |{
          |  "container": "mycontainer",
          |  "credentials": { "accountName": "myname", "accountKey": "mykey" },
          |  "storageUrl": "https://myaccount.blob.core.windows.net/",
          |  "maxQueueSize": 10
          |}
        """.stripMargin

      s.decodeOption[AzureConfig] must_=== Some(
        AzureConfig(
          ContainerName("mycontainer"),
          Some(AzureCredentials(AccountName("myname"), AccountKey("mykey"))),
          Azure.mkStdStorageUrl(AccountName("myaccount")),
          Some(MaxQueueSize(10))))
    }

    "succeeds reading config without credentials" >> {
      val s =
        """
          |{
          |  "container": "mycontainer",
          |  "storageUrl": "https://myaccount.blob.core.windows.net/",
          |  "maxQueueSize": 10
          |}
        """.stripMargin

      s.decodeOption[AzureConfig] must_=== Some(
        AzureConfig(
          ContainerName("mycontainer"),
          None,
          Azure.mkStdStorageUrl(AccountName("myaccount")),
          Some(MaxQueueSize(10))))
    }

    "fails reading config with incomplete credentials" >> {
      val s =
        """
          |{
          |  "container": "mycontainer",
          |  "credentials": { "accountName":"myname" },
          |  "storageUrl": "https://myaccount.blob.core.windows.net/",
          |  "maxQueueSize": 10
          |}
        """.stripMargin

      s.decodeOption[AzureConfig] must_=== None
    }

    "fails reading config with non-positive maxQueueSize" >> {
      val s =
        """
          |{
          |  "container": "mycontainer",
          |  "storageUrl": "https://myaccount.blob.core.windows.net/",
          |  "maxQueueSize": 0
          |}
        """.stripMargin

      s.decodeOption[AzureConfig] must_=== None
    }
  }
}