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

import slamdata.Predef.Unit
import quasar.blobstore.{BlobstoreStatus, ops}
import quasar.blobstore.azure.requests.ContainerPropsArgs
import quasar.blobstore.services.StatusService

import cats.effect.Async
import cats.syntax.applicative._
import com.microsoft.azure.storage.blob.ContainerURL
import com.microsoft.azure.storage.blob.models.{ContainerGetPropertiesResponse, LeaseAccessConditions}
import com.microsoft.rest.v2.Context

class AzureStatusService[F[_]: Async](args: ContainerPropsArgs) extends StatusService[F, BlobstoreStatus] {
  override def status: F[BlobstoreStatus] =
    ops.service[F, Unit, ContainerPropsArgs, ContainerGetPropertiesResponse, BlobstoreStatus, F[BlobstoreStatus]](
      _ => args.pure[F],
      requests.containerPropsRequest[F],
      _ => BlobstoreStatus.ok().pure[F],
      handlers.recoverToBlobstoreStatus[F, BlobstoreStatus]
    ).apply(())

}

object AzureStatusService {
  def apply[F[_]: Async](containerURL: ContainerURL): AzureStatusService[F] =
    new AzureStatusService[F](ContainerPropsArgs(containerURL, new LeaseAccessConditions, Context.NONE))
}
