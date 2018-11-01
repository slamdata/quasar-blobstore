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
import quasar.api.resource.{ResourceName, ResourcePath, ResourcePathType}
import quasar.blobstore.Blobstore
import quasar.connector.{MonadResourceErr, ResourceError}

import java.lang.Integer
import java.nio.ByteBuffer
import scala.collection.JavaConverters._

import cats.effect._
import cats.implicits._
import com.microsoft.azure.storage.blob._
import com.microsoft.azure.storage.blob.models._
import com.microsoft.rest.v2.Context
import fs2.{RaiseThrowable, Stream}
import io.reactivex._

class AzureBlobstore[F[_]: ConcurrentEffect: MonadResourceErr: RaiseThrowable](
  containerURL: ContainerURL) extends Blobstore[F] {

  private val F = ConcurrentEffect[F]

  def get(path: ResourcePath): Stream[F, ByteBuffer] = {
    val bufs: F[Stream[F, ByteBuffer]] = for {
      single <- download(pathToAzurePath(path), BlobRange.DEFAULT)
      r <- rx.singleToAsync(single)
      s <- F.delay(rx.flowableToStream(r.body(new ReliableDownloadOptions)))
    } yield s

    Stream.eval(bufs).flatten
      .handleErrorWith {
        case ex: StorageException if ex.statusCode() == 404 =>
          Stream.raiseError(ResourceError.throwableP(ResourceError.pathNotFound(path)))
      }
  }

  def isResource(path: ResourcePath): F[Boolean] = {
    val res: F[Boolean] = for {
      single <- download(pathToAzurePath(path), new BlobRange().withCount(java.lang.Long.valueOf(0L)))
      _ <- rx.singleToAsync(single)
    } yield true

    res.recover {
      case _: StorageException => false
    }
  }

  def list(path: ResourcePath): F[Option[Stream[F, (ResourceName, ResourcePathType)]]] = {
    val resp: F[ContainerListBlobHierarchySegmentResponse] = for {
      single <- listBlobs(None, pathToOptions(path))
      r <- rx.singleToAsync(single)
    } yield r

    resp.map(r => r.body.segment).map { segm =>
      if (segm == null) None
      else {
        val l = segm.blobItems.asScala.map(blobItemToNameType(_, path)) ++
          segm.blobPrefixes.asScala.map(blobPrefixToNameType(_, path))
        Stream.emits(l).covary[F].some
      }
    }
  }

  private def blobItemToNameType(i: BlobItem, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(i.name)), ResourcePathType.LeafResource)

  private def blobPrefixToNameType(p: BlobPrefix, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(p.name)), ResourcePathType.Prefix)

  private def download(path: String, range: BlobRange): F[Single[DownloadResponse]] = {
    val blobUrl = containerURL.createBlobURL(path)
    F.delay(blobUrl.download(range, BlobAccessConditions.NONE, false, Context.NONE))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def listBlobs(marker: Option[String], options: ListBlobsOptions): F[Single[ContainerListBlobHierarchySegmentResponse]] =
    F.delay(containerURL.listBlobsHierarchySegment(marker.orNull, "/", options, Context.NONE))

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def normalize(name: String): String =
    if (name.endsWith("/")) normalize(name.substring(0, name.length - 1))
    else name

  private def pathToAzurePath(path: ResourcePath): String = {
    val names = ResourcePath.resourceNamesIso.get(path).map(_.value).toList
    names.mkString("/")
  }

  private def pathToOptions(path: ResourcePath): ListBlobsOptions =
    new ListBlobsOptions()
      .withMaxResults(Integer.valueOf(5000))
      .withPrefix(pathToPrefix(path))

  private def pathToPrefix(path: ResourcePath): String = {
    val names = ResourcePath.resourceNamesIso.get(path).map(_.value).toList
    if (names.isEmpty) ""
    else names.mkString("", "/", "/")
  }

  private def simpleName(s: String): String = {
    val ns = normalize(s)
    ns.substring(ns.lastIndexOf('/') + 1)
  }
}
