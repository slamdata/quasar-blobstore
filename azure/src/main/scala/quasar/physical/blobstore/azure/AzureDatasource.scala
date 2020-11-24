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

package quasar.physical
package blobstore
package azure

import slamdata.Predef._

import quasar.api.datasource.DatasourceType
import quasar.blobstore.azure.{converters => _, _}
import quasar.blobstore.services._
import quasar.connector.MonadResourceErr

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.effect.concurrent.Ref
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.models.BlobProperties

object AzureDatasource {
  val dsType: DatasourceType = DatasourceType("azure", 1L)

  def withRefresh[F[_]: ConcurrentEffect, A](
      refClient: Ref[F, Expires[BlobContainerAsyncClient]],
      refreshToken: F[Unit],
      f : BlobContainerAsyncClient => A)
      : F[A] =
    for {
      _ <- refreshToken
      client <- refClient.get
    } yield f(client.value)

  def mk[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer](cfg: AzureConfig)
      : F[BlobstoreDatasource[F, BlobProperties]] =
    for {
      (refClient, refreshToken) <- Azure.refContainerClient[F](cfg)
    } yield
      BlobstoreDatasource[F, BlobProperties](
        dsType,
        cfg.format,
        withRefresh[F, StatusService[F]](refClient, refreshToken, AzureStatusService.mk[F](_)),
        withRefresh[F, ListService[F]](refClient, refreshToken, AzureListService.mk[F](_)),
        withRefresh[F, PropsService[F, BlobProperties]](refClient, refreshToken, AzurePropsService.mk[F](_)),
        withRefresh[F, GetService[F]](refClient, refreshToken, AzureGetService.mk(_)))

}
