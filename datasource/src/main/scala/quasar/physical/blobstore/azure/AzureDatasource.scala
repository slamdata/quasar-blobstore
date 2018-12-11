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
import quasar.api.resource.{ResourceName, ResourcePath, ResourcePathType}
import quasar.blobstore.{BlobstoreStatus, ResourceType}
import quasar.blobstore.azure.{converters => azureConverters, _}
import quasar.blobstore.paths.{BlobPath, BlobstorePath, PrefixPath}
import quasar.blobstore.services.{GetService, ListService, PropsService}
import quasar.connector.{MonadResourceErr, ResourceError}
import quasar.connector.ParsableType.JsonVariant

import java.lang.Integer

import cats.Monad
import cats.data.Kleisli
import cats.effect.{Async, ConcurrentEffect}
import cats.instances.int._
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.eq._
import cats.syntax.functor._
import com.microsoft.azure.storage.blob.{BlobURL, ContainerURL, StorageException}
import eu.timepit.refined.auto._
import fs2.{RaiseThrowable, Stream}

class AzureDatasource[
  F[_]: Monad: MonadResourceErr: RaiseThrowable, BP, PP](
  resourcePathToBlobPath: Kleisli[F, ResourcePath, BP],
  resourcePathToPrefixPath: Kleisli[F, ResourcePath, PP],
  status: F[BlobstoreStatus],
  prefixPathList: ListService[F, PP, (ResourceName, ResourcePathType)],
  blobPathIsValid: PropsService[F, BP, Boolean],
  blobPathGet: GetService[F, BP],
  jsonVariant: JsonVariant)
  extends BlobstoreDatasource[F, BP, PP](
    AzureDatasource.dsType,
    jsonVariant,
    resourcePathToBlobPath,
    resourcePathToPrefixPath,
    status,
    prefixPathList,
    blobPathIsValid,
    blobPathGet)

object AzureDatasource {
  val dsType: DatasourceType = DatasourceType("azure", 1L)

  private def isResource[F[_]: Async](toBlobUrl: Kleisli[F, BlobPath, BlobURL]): PropsService[F, BlobPath, Boolean] = {
    AzurePropsService.mk[F, BlobPath, Boolean](
      toBlobUrl,
      Kleisli(_ => true.pure[F]),
      _.recover { case _: StorageException => false })
  }

  private def errorHandler[F[_]: RaiseThrowable, A](path: BlobPath): Throwable => Stream[F, A] = {
    case ex: StorageException if ex.statusCode() === 404 =>
      Stream.raiseError(ResourceError.throwableP(ResourceError.pathNotFound(converters.blobPathToResourcePath(path))))
  }

  private def get[F[_]: ConcurrentEffect](
    toBlobUrl: Kleisli[F, BlobPath, BlobURL],
    maxQueueSize: MaxQueueSize)
      : GetService[F, BlobPath] =
    AzureGetService.mk[F, BlobPath](toBlobUrl, maxQueueSize, errorHandler[F, Byte])

  private def list[F[_]: Async](containerURL: ContainerURL): ListService[F, PrefixPath, BlobstorePath] = {
    AzureListService.mk[F, PrefixPath, BlobstorePath](
      azureConverters.prefixPathToListBlobOptionsK(details = None, maxResults = Some(Integer.valueOf(5000))),
      azureConverters.toBlobstorePathsK,
      containerURL,
      x => x)
  }

  def mk[F[_]: ConcurrentEffect: MonadResourceErr](cfg: AzureConfig): F[AzureDatasource[F, BlobPath, PrefixPath]] =
    Azure.mkContainerUrl[F](cfg) map {c =>
      val blobPathToBlobURLK = azureConverters.blobPathToBlobURLK(c)

      new AzureDatasource[F, BlobPath, PrefixPath](
        converters.resourcePathToBlobPathK[F],
        converters.resourcePathToPrefixPathK[F],
        AzureStatusService(c).status,
        list(c).map(_.map(_.map(converters.toResourceNameType))),
        isResource[F](blobPathToBlobURLK),
        get[F](blobPathToBlobURLK, cfg.maxQueueSize.getOrElse(MaxQueueSize.default)),
        toJsonVariant(cfg.resourceType))
    }

  private def toJsonVariant(resourceType: ResourceType): JsonVariant =
    resourceType match {
      case ResourceType.Json => JsonVariant.ArrayWrapped
      case ResourceType.LdJson => JsonVariant.LineDelimited
    }
}
