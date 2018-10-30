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
import quasar.EffectfulQSpec
import quasar.api.resource.{ResourceName, ResourcePath, ResourcePathType}
import quasar.connector.{Datasource, QueryResult}

import scala.concurrent.ExecutionContext.Implicits.global

import cats.data.OptionT
import cats.effect.Effect
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream

abstract class BlobstoreDatasourceSpec[F[_]: Effect] extends EffectfulQSpec[F] {

  val F = Effect[F]

  def datasource: F[Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]]

  val spanishResourceName1 = ResourceName("El veloz murciélago hindú")
  val spanishResourcePrefix = ResourcePath.root() / ResourceName("testData") / spanishResourceName1 / ResourceName("comía feliz cardillo y kiwi") / ResourceName("La cigüeña tocaba el saxofón")
  val spanishResourceLeaf = ResourceName("detrás del palenque de paja")
  val spanishResource = spanishResourcePrefix / spanishResourceLeaf

  "prefixedChildPaths" >> {

    "list nested children" >>* {
      assertPrefixedChildPaths(
        ResourcePath.root() / ResourceName("dir1") / ResourceName("dir2") / ResourceName("dir3"),
        List(ResourceName("flattenable.data") -> ResourcePathType.leafResource))
    }

    "list nested children 2" >>* {
      assertPrefixedChildPaths(
        ResourcePath.root() / ResourceName("prefix3") / ResourceName("subprefix5"),
        List(ResourceName("cars2.data") -> ResourcePathType.leafResource))
    }

    "list children at the root of the bucket" >>* {
      assertPrefixedChildPaths(
        ResourcePath.root(),
        List(
          //ResourceName("dir1") -> ResourcePathType.prefix,
          ResourceName("extraSmallZips.data") -> ResourcePathType.leafResource,
          ResourceName("prefix3") -> ResourcePathType.prefix,
          ResourceName("testdata") -> ResourcePathType.prefix))
    }
  }

  def assertPrefixedChildPaths(path: ResourcePath, expected: List[(ResourceName, ResourcePathType)]) =
    for {
      ds <- datasource
      res <- OptionT(ds.prefixedChildPaths(path))
        .getOrElseF(F.raiseError(new Exception(s"Failed to list resources under $path")))
        .flatMap(_.compile.toList).map { _ must_== expected }
    } yield res
}
