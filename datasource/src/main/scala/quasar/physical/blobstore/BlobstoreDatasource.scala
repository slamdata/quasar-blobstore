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

package quasar.physical.blobstore

import slamdata.Predef._
import quasar.api.datasource.DatasourceType
import quasar.api.resource.{ResourceName, ResourcePath, ResourcePathType}
import quasar.blobstore.{BlobstoreStatus, Converter}
import quasar.connector._
import ParsableType.JsonVariant
import quasar.connector.datasource.LightweightDatasource
import quasar.contrib.scalaz.MonadError_

import cats.Monad
import cats.effect.IO
import cats.syntax.functor._
import cats.syntax.flatMap._
import fs2.{RaiseThrowable, Stream}

class BlobstoreDatasource[F[_]: Monad: MonadResourceErr: RaiseThrowable, BP, PP](
  val kind: DatasourceType,
  jvar: JsonVariant,
  blobstoreStatus: F[BlobstoreStatus],
  list: PP => F[Option[Stream[F, (ResourceName, ResourcePathType)]]]  ,
  isResource: BP => F[Boolean],
  get: BP => Stream[F, Byte])(
  implicit CBP: Converter[F, ResourcePath, BP],
  CPP: Converter[F, ResourcePath, PP])
  extends LightweightDatasource[F, Stream[F, ?], QueryResult[F]] {

  override def evaluate(path: ResourcePath): F[QueryResult[F]] =
  for {
    blobPath <- CBP.convert(path)
    bytes = get(blobPath)
    qr = QueryResult.typed[F](ParsableType.json(jvar, false), bytes)
  } yield qr

  override def pathIsResource(path: ResourcePath): F[Boolean] =
    CBP.convert(path).flatMap(isResource(_))

  override def prefixedChildPaths(prefixPath: ResourcePath)
      : F[Option[Stream[F, (ResourceName, ResourcePathType)]]] =
    CPP.convert(prefixPath).flatMap(list(_))

  def asDsType: Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]] = this

  def status: F[BlobstoreStatus] = blobstoreStatus
}

object BlobstoreDatasource {

  import shims._

  implicit val ioMonadResourceErr: MonadError_[IO, ResourceError] =
    MonadError_.facet[IO](ResourceError.throwableP)

}
