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
import quasar.blobstore.{Converter, ops}
import quasar.blobstore.azure.requests.DownloadArgs
import quasar.blobstore.services.GetService

import cats.effect.ConcurrentEffect
import cats.syntax.applicative._
import cats.syntax.flatMap._
import com.microsoft.azure.storage.blob._
import com.microsoft.rest.v2.Context
import fs2.Stream

class AzureGetService[F[_]: ConcurrentEffect, P](
    args: BlobURL => DownloadArgs,
    reliableDownloadOptions: ReliableDownloadOptions,
    maxQueueSize: MaxQueueSize,
    errorHandler: P => Throwable => Stream[F, Byte])(
    implicit CP: Converter[F, P, BlobURL])
  extends GetService[F, P] {

  override def get(path: P): Stream[F, Byte] =
    ops.service[F, P, DownloadArgs, DownloadResponse, Stream[F, Byte], Stream[F, Byte]](
      CP.convert(_) >>= (args(_).pure[F]),
      requests.downloadRequest[F],
      handlers.toByteStream(reliableDownloadOptions, maxQueueSize),
      Stream.force(_).handleErrorWith(errorHandler(path))
    ).apply(path)

}

object AzureGetService {
  def apply[F[_]: ConcurrentEffect, P: Converter[F, ?, BlobURL]](
    maxQueueSize: MaxQueueSize,
    errorHandler: P => Throwable => Stream[F, Byte])
      : AzureGetService[F, P] =
    new AzureGetService[F, P](
      url => DownloadArgs(url, BlobRange.DEFAULT, BlobAccessConditions.NONE, false, Context.NONE),
      new ReliableDownloadOptions,
      maxQueueSize,
      errorHandler)
}
