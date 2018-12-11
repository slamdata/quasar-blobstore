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
import quasar.api.resource.{ResourceName, ResourcePath, ResourcePathType}
import quasar.blobstore.Converter
import quasar.blobstore.paths._

import cats.Applicative
import cats.data.Kleisli
import cats.syntax.applicative._
import scalaz.IList

object converters {

  implicit def resourcePathToBlobPath[F[_]: Applicative]: Converter[F, ResourcePath, BlobPath] =
    Converter.pure[F, ResourcePath, BlobPath](toBlobPath)

  implicit def resourcePathToBlobPathK[F[_]: Applicative]: Kleisli[F, ResourcePath, BlobPath] =
    Kleisli[F, ResourcePath, BlobPath](toBlobPath(_).pure[F])

  implicit def resourcePathToPrefixPath[F[_]: Applicative]: Converter[F, ResourcePath, PrefixPath] =
    Converter.pure[F, ResourcePath, PrefixPath](toPrefixPath)

  def toPrefixPath(path: ResourcePath): PrefixPath =
    PrefixPath(toPath(path))

  def toBlobPath(path: ResourcePath): BlobPath =
    BlobPath(toPath(path))

  def toPath(path: ResourcePath): Path =
    ResourcePath.resourceNamesIso.get(path).map(n => PathElem(n.value)).toList

  def blobPathToResourcePath(path: BlobPath): ResourcePath =
    ResourcePath.resourceNamesIso(IList.fromList(path.path.map(e => ResourceName(e.value))))

  def toResourceNameType(p: BlobstorePath): (ResourceName, ResourcePathType) =
    (toResourceName(p).getOrElse(ResourceName("")), toResourceType(p))

  def toResourceType(p: BlobstorePath): ResourcePathType =
    p match {
      case BlobPath(_) => ResourcePathType.LeafResource
      case PrefixPath(_) => ResourcePathType.Prefix
    }

  def toResourceName(p: BlobstorePath): Option[ResourceName] =
    p.path.lastOption.map(p => ResourceName(p.value))

}
