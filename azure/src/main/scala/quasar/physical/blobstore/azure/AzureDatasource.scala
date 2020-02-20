/*
 * Copyright 2014–2020 SlamData Inc.
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

package quasar.physical
package blobstore
package azure

import slamdata.Predef._
import quasar.api.datasource.DatasourceType
import quasar.blobstore.azure.{converters => _, _}
import quasar.blobstore.services.{GetService, ListService, PropsService, StatusService}
import quasar.connector.{MonadResourceErr, DataFormat}

import cats.Monad
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.syntax.functor._
import com.microsoft.azure.storage.blob.models.BlobGetPropertiesResponse
import eu.timepit.refined.auto._

class AzureDatasource[
  F[_]: Monad: MonadResourceErr](
  statusService: StatusService[F],
  listService: ListService[F],
  propsService: PropsService[F, BlobGetPropertiesResponse],
  getService: GetService[F],
  format: DataFormat)
  extends BlobstoreDatasource[F, BlobGetPropertiesResponse](
    AzureDatasource.dsType,
    format,
    statusService,
    listService,
    propsService,
    getService)

object AzureDatasource {
  val dsType: DatasourceType = DatasourceType("azure", 1L)

  def mk[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr](cfg: AzureConfig): F[AzureDatasource[F]] =
    Azure.mkContainerUrl[F](cfg) map { c =>

      new AzureDatasource[F](
        AzureStatusService.mk(c.value),
        AzureListService.mk[F](c.value),
        AzurePropsService.mk[F](c.value) mapF
          handlers.recoverStorageException[F, Option[BlobGetPropertiesResponse]] map
          (_.flatten),
        AzureGetService.mk(c.value, cfg.maxQueueSize.getOrElse(MaxQueueSize.default)),
        cfg.format)
    }
}
