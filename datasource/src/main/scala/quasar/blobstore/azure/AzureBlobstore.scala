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
import quasar.contrib.std.errorNotImplemented

import java.lang.Integer
import scala.collection.JavaConverters._

import cats.effect._
import cats.implicits._
import com.microsoft.azure.storage.blob._
import com.microsoft.azure.storage.blob.models._
import com.microsoft.rest.v2.Context
import fs2.Stream
import io.reactivex._
import io.reactivex.observers._

object AzureBlobstore {

  final class AsyncObserver[A](cb: Either[Throwable, A] => Unit)
    extends DisposableSingleObserver[A] {

    override def onStart(): Unit = ()

    override def onSuccess(a: A) =
      cb(Right(a))

    override def onError(t: Throwable) =
      cb(Left(t))
  }
}

class AzureBlobstore[F[_]: Async](
  containerURL: ContainerURL) extends Blobstore[F] {

  import AzureBlobstore._

  private val F = Async[F]

  def get(path: ResourcePath): Stream[F, Byte] = errorNotImplemented

  def isResource(path: ResourcePath): F[Boolean] = errorNotImplemented

  def list(path: ResourcePath): Stream[F, (ResourceName, ResourcePathType)] = {
    val resp: F[ContainerListBlobHierarchySegmentResponse] = for {
      single <- listBlobs(None, pathToOptions(path))
      r <- mkAsync(single.subscribe)
    } yield r

    Stream.eval(resp).flatMap { r =>
      val segm = r.body.segment
      val l =
        if (segm == null) List.empty
        else segm.blobItems.asScala.map(blobItemToNameType(_, path)) ++
          segm.blobPrefixes.asScala.map(blobPrefixToNameType(_, path))
      Stream.emits(l).covary[F]
    }
  }

  private def blobItemToNameType(i: BlobItem, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(i.name)), ResourcePathType.LeafResource)

  private def blobPrefixToNameType(p: BlobPrefix, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(p.name)), ResourcePathType.Prefix)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def listBlobs(marker: Option[String], options: ListBlobsOptions): F[Single[ContainerListBlobHierarchySegmentResponse]] =
    F.delay(containerURL.listBlobsHierarchySegment(marker.orNull, "/", options, Context.NONE))

  private def mkAsync[A](f: SingleObserver[A] => Unit): F[A] =
    F.async[A] { cb: (Either[Throwable, A] => Unit) =>
      f(new AsyncObserver(cb))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def normalize(name: String): String =
    if (name.endsWith("/")) normalize(name.substring(0, name.length - 1))
    else name

  private def pathToOptions(path: ResourcePath): ListBlobsOptions =
    ListBlobsOptions.DEFAULT
      .withMaxResults(Integer.valueOf(1000))
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
