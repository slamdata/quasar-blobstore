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

package quasar.physical
package blobstore
package azure

import slamdata.Predef._
import quasar.api.datasource.DatasourceType
import quasar.api.resource.ResourcePath
import quasar.blobstore.{Converter, ResourceType}
import quasar.blobstore.azure.{converters => azureConverters, _}
import quasar.blobstore.paths.BlobPath
import quasar.connector.{MonadResourceErr, ResourceError}
import quasar.connector.ParsableType.JsonVariant
import quasar.physical.blobstore.BlobstoreDatasource

import cats.Applicative
import cats.effect.ConcurrentEffect
import cats.instances.int._
import cats.syntax.eq._
import cats.syntax.functor._
import com.microsoft.azure.storage.blob.StorageException
import eu.timepit.refined.auto._
import fs2.{RaiseThrowable, Stream}

class AzureDatasource[
  F[_]: Applicative: MonadResourceErr: RaiseThrowable,
  BP: Converter[F, ResourcePath, ?]](
  statusService: AzureStatusService[F],
  getService: AzureGetService[F, BP],
  azureBlobstore: AzureBlobstore[F],
  jsonVariant: JsonVariant)
  extends BlobstoreDatasource[F, BP](AzureDatasource.dsType, jsonVariant, statusService.status, getService, azureBlobstore)

object AzureDatasource {
  val dsType: DatasourceType = DatasourceType("azure", 1L)

  private def errorHandler[F[_]: RaiseThrowable, A](path: BlobPath): Throwable => Stream[F, A] = {
    case ex: StorageException if ex.statusCode() === 404 =>
      Stream.raiseError(ResourceError.throwableP(ResourceError.pathNotFound(converters.blobPathToResourcePath(path))))
  }

  def mk[F[_]: ConcurrentEffect: MonadResourceErr](cfg: AzureConfig): F[AzureDatasource[F, BlobPath]] =
    Azure.mkContainerUrl[F](cfg) map {c =>
      import converters._
      implicit val CBP = azureConverters.blobPathToBlobURL(c)

      val ds = new AzureDatasource[F, BlobPath](
        AzureStatusService(c),
        AzureGetService[F, BlobPath](cfg.maxQueueSize.getOrElse(MaxQueueSize.default), errorHandler[F, Byte]),
        new AzureBlobstore(c),
        toJsonVariant(cfg.resourceType))
      ds
    }

  private def toJsonVariant(resourceType: ResourceType): JsonVariant =
    resourceType match {
      case ResourceType.Json => JsonVariant.ArrayWrapped
      case ResourceType.LdJson => JsonVariant.LineDelimited
    }
}
