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

import quasar.blobstore.azure.requests.BlobPropsArgs
import quasar.blobstore.{Converter, ops}
import quasar.blobstore.services.PropsService

import cats.effect.Async
import cats.syntax.functor._
import com.microsoft.azure.storage.blob.{BlobAccessConditions, BlobURL}
import com.microsoft.azure.storage.blob.models.BlobGetPropertiesResponse
import com.microsoft.rest.v2.Context

class AzurePropsService[F[_]: Async, P, R](
    mkArgs: BlobURL => BlobPropsArgs,
    handler: F[R] => F[R])(
    implicit CP: Converter[F, P, BlobURL], CR: Converter[F, BlobGetPropertiesResponse, R])
  extends PropsService[F, P, R] {

  override def props(path: P): F[R] =
    ops.service[F, P, BlobPropsArgs, BlobGetPropertiesResponse, R, F[R]](
      CP.convert(_).map(mkArgs),
      requests.blobPropsRequest[F],
      CR.convert(_),
      handler
    ).apply(path)

}

object AzurePropsService {
  def apply[
      F[_]: Async, P: Converter[F, ?, BlobURL],
      R: Converter[F, BlobGetPropertiesResponse, ?]](
      handler: F[R] => F[R]): AzurePropsService[F, P, R] =
    new AzurePropsService[F, P, R](
      BlobPropsArgs(_, BlobAccessConditions.NONE, Context.NONE),
      handler)
}
