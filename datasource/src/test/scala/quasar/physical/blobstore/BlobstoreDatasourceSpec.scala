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
import quasar.connector.{Datasource, QueryResult, ResourceError}

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global

import cats.data.OptionT
import cats.effect.Effect
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import org.specs2.matcher.MatchResult

abstract class BlobstoreDatasourceSpec[F[_]: Effect] extends EffectfulQSpec[F] {

  val F = Effect[F]

  def datasource: F[Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]]

  val nonExistentPath =
    ResourcePath.root() / ResourceName("does") / ResourceName("not") / ResourceName("exist")

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
          ResourceName("extraSmallZips.data") -> ResourcePathType.leafResource,
          ResourceName("dir1") -> ResourcePathType.prefix,
          ResourceName("prefix3") -> ResourcePathType.prefix,
          ResourceName("testdata") -> ResourcePathType.prefix))
    }

    "return none for non-existing path" >>* {
      assertPrefixedChildPathsNone(nonExistentPath)
    }
  }

  "evaluate" >> {
    "read line-delimited JSON" >>* {
      assertResultBytes(
        datasource,
        ResourcePath.root() / ResourceName("testdata") / ResourceName("lines.json"),
        "[1, 2]\n[3, 4]\n".getBytes(StandardCharsets.UTF_8))
    }

    "read array JSON" >>* {
      assertResultBytes(
        datasource,
        ResourcePath.root() / ResourceName("testdata") / ResourceName("array.json"),
        "[[1, 2], [3, 4]]\n".getBytes(StandardCharsets.UTF_8))
    }

    "reading a non-existent file raises ResourceError.PathNotFound" >>* {
      assertPathNotFound(
        datasource,
        ResourcePath.root() / ResourceName("does-not-exist")
      )
    }
  }

  "pathIsResource" >> {
    "the root of a bucket with a trailing slash is not a resource" >>* {
      assertPathIsResource(
        datasource,
        ResourcePath.root() / ResourceName(""),
        false)
    }

    "the root of a bucket is not a resource" >>* {
      assertPathIsResource(
        datasource,
        ResourcePath.root(),
        false)
    }

    "a prefix without contents is not a resource" >>* {
      assertPathIsResource(
        datasource,
        ResourcePath.root() / ResourceName("testdata"),
        false)
    }

    "an actual file is a resource" >>* {
      assertPathIsResource(
        datasource,
        ResourcePath.root() / ResourceName("testdata") / ResourceName("array.json"),
        true)
    }
  }

  def assertPathIsResource(
      datasource: F[Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]],
      path: ResourcePath,
      expected: Boolean): F[MatchResult[Any]] =
    for {
      ds <- datasource
      r <- ds.pathIsResource(path).map(_ must_=== expected)
    } yield r


  def assertPathNotFound(
      datasource: F[Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]],
      path: ResourcePath): F[MatchResult[Any]] =
    datasource flatMap { ds =>
      ds.evaluate(path) flatMap {
        case QueryResult.Typed(_, data) =>
          data.attempt.compile.toList.map(_.map(_.leftMap(ResourceError.throwableP.getOption)) must_===
            List(Some(ResourceError.pathNotFound(path)).asLeft))

        case r =>
          ko(s"Unexpected QueryResult: $r").pure[F]
      }
    }

  def assertPrefixedChildPaths(path: ResourcePath, expected: List[(ResourceName, ResourcePathType)]) =
    for {
      ds <- datasource
      res <- OptionT(ds.prefixedChildPaths(path))
        .getOrElseF(F.raiseError(new Exception(s"Failed to list resources under $path")))
        .flatMap(_.compile.toList).map { _ must_== expected }
    } yield res

  def assertPrefixedChildPathsNone(path: ResourcePath) =
    for {
      ds <- datasource
      res <- ds.prefixedChildPaths(path).map { _ must_== None }
    } yield res

  def assertResultBytes(
      datasource: F[Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]],
      path: ResourcePath,
      expected: Array[Byte]): F[MatchResult[Any]] =
    datasource flatMap { ds =>
      ds.evaluate(path) flatMap {
        case QueryResult.Typed(_, data) =>
          data.compile.to[Array].map(_ must_=== expected)

        case _ =>
          ko("Unexpected QueryResult").pure[F]
      }
    }
}
