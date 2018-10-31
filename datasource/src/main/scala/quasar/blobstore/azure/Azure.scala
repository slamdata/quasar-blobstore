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

package quasar.blobstore.azure

import slamdata.Predef._

import java.net.URL

import cats.effect.{Effect, Sync}
import com.microsoft.azure.storage.blob._

object Azure {

  def mkStdStorageUrl(name: AccountName): StorageUrl =
    StorageUrl(s"https://${name.value}.blob.core.windows.net/")

  def mkCredentials(cred: Option[AzureCredentials]): ICredentials =
    cred match {
      case None => new AnonymousCredentials
      case Some(c) => new SharedKeyCredentials(c.accountName.value, c.accountKey.value)
    }

  def mkContainerUrl[F[_]: Effect](cfg: AzureConfig)(implicit F: Sync[F]): F[ContainerURL] =
    F.delay {
      val url = new URL(cfg.storageUrl.value)
      val serviceUrl = new ServiceURL(url,
        StorageURL.createPipeline(mkCredentials(cfg.credentials), new PipelineOptions))
      serviceUrl.createContainerURL(cfg.containerName.value)
    }

}
