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
import quasar.blobstore.Blobstore
import quasar.connector._
import ParsableType.JsonVariant
import quasar.connector.datasource.LightweightDatasource
import quasar.contrib.scalaz.MonadError_

import cats.Applicative
import cats.effect.IO
import cats.syntax.applicative._
import fs2.{RaiseThrowable, Stream}

class BlobstoreDatasource[F[_]: Applicative: MonadResourceErr: RaiseThrowable](
  val kind: DatasourceType,
  blobstore: Blobstore[F])
  extends LightweightDatasource[F, Stream[F, ?], QueryResult[F]] {

  private val jvar = JsonVariant.LineDelimited

  override def evaluate(path: ResourcePath): F[QueryResult[F]] = {
    val bytes = blobstore.get(path)
    QueryResult.typed(ParsableType.json(jvar, false), bytes).pure[F]
  }

  override def pathIsResource(path: ResourcePath): F[Boolean] =
    blobstore.isResource(path)

  override def prefixedChildPaths(prefixPath: ResourcePath)
      : F[Option[Stream[F, (ResourceName, ResourcePathType)]]] =
    blobstore.list(prefixPath)

  def asDsType: Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]] = this
}

object BlobstoreDatasource {

  import shims._

  implicit val ioMonadResourceErr: MonadError_[IO, ResourceError] =
    MonadError_.facet[IO](ResourceError.throwableP)

}
