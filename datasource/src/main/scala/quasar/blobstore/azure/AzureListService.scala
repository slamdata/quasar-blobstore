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
import quasar.blobstore.{Converter, ops}
import quasar.blobstore.services.ListService

import cats.effect.Async
import cats.syntax.functor._
import com.microsoft.azure.storage.blob.{ContainerURL, ListBlobsOptions}
import com.microsoft.azure.storage.blob.models.ContainerListBlobHierarchySegmentResponse
import com.microsoft.rest.v2.Context
import fs2.Stream

class AzureListService[F[_]: Async, P, R](
    mkArgs: ListBlobsOptions => ListBlobHierarchyArgs,
    handler: F[Option[Stream[F, R]]] => F[Option[Stream[F, R]]])(
    implicit CP: Converter[F, P, ListBlobsOptions], CR: Converter[F, (ContainerListBlobHierarchySegmentResponse, P), Option[Stream[F, R]]])
  extends ListService[F, P, R] {

  override def list(path: P): F[Option[Stream[F, R]]] =
    ops.service[F, P, ListBlobHierarchyArgs, ContainerListBlobHierarchySegmentResponse, Option[Stream[F, R]], F[Option[Stream[F, R]]]](
      CP.convert(_).map(mkArgs),
      requests.listRequest[F],
      res => CR.convert((res, path)),
      handler
    ).apply(path)
}

object AzureListService {
  def apply[F[_]: Async, P: Converter[F, ?, ListBlobsOptions], R: λ[A => Converter[F, (ContainerListBlobHierarchySegmentResponse, P), Option[Stream[F, A]]]]](
    containerURL: ContainerURL,
    handler: F[Option[Stream[F, R]]] => F[Option[Stream[F, R]]]): AzureListService[F, P, R] =
    new AzureListService[F, P, R](
      ListBlobHierarchyArgs(containerURL, None, "/", _, Context.NONE),
      handler
    )
}
