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
import quasar.blobstore.azure.requests.ListBlobHierarchyArgs
import quasar.blobstore.services.ListService

import cats.data.Kleisli
import cats.effect.Async
import com.microsoft.azure.storage.blob.{ContainerURL, ListBlobsOptions}
import com.microsoft.azure.storage.blob.models.ContainerListBlobHierarchySegmentResponse
import com.microsoft.rest.v2.Context
import fs2.Stream

object AzureListService {
  def apply[F[_]: Async, P, R](
      toListBlobsOption: Kleisli[F, P, ListBlobsOptions],
      toResponse: Kleisli[F, ContainerListBlobHierarchySegmentResponse, Option[Stream[F, R]]],
      mkArgs: ListBlobsOptions => ListBlobHierarchyArgs)
      : ListService[F, P, R] =
    toListBlobsOption map mkArgs andThen requests.listRequestK andThen toResponse


  def mk[F[_]: Async, P, R](
      toListBlobsOptions: Kleisli[F, P, ListBlobsOptions],
      toResponse: Kleisli[F, ContainerListBlobHierarchySegmentResponse, Option[Stream[F, R]]],
      containerURL: ContainerURL)
      : ListService[F, P, R] =
    AzureListService[F, P, R](
      toListBlobsOptions,
      toResponse,
      ListBlobHierarchyArgs(containerURL, None, "/", _, Context.NONE))
}
