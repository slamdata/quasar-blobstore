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
import quasar.contrib.std.errorNotImplemented

import java.lang.Integer
import java.nio.ByteBuffer
import scala.collection.JavaConverters._

import cats.effect._
import cats.implicits._
import com.microsoft.azure.storage.blob._
import com.microsoft.azure.storage.blob.models._
import com.microsoft.rest.v2.Context
import fs2.concurrent.Queue
import fs2.{RaiseThrowable, Stream}
import io.reactivex._
import io.reactivex.functions.{Action, Consumer}
import io.reactivex.observers._

object AzureBlobstore {

  final class AsyncConsumer[A] {

    @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
    private var callback: Either[Throwable, Option[A]] => Unit = _

    def setCallback(cb: Either[Throwable, Option[A]] => Unit): Unit = {
      this.callback = cb
    }

    def onNext: Consumer[A] = { a =>
      if (callback != null) callback(Right(a.some))
      else ()
    }

    def onError: Consumer[Throwable] = { t =>
      if (callback != null) callback(Left(t))
      else ()
    }

    def onComplete: Action = () => {
      if (callback != null) callback(Right(none))
      else ()
    }

  }

  final class AsyncObserver[A] extends DisposableSingleObserver[A] {

    @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
    private var callback: Either[Throwable, A] => Unit = _

    def setCallback(cb: Either[Throwable, A] => Unit): Unit = {
      this.callback = cb
    }

    override def onStart(): Unit = ()

    override def onSuccess(a: A) =
      if (callback != null) callback(Right(a))
      else ()

    override def onError(t: Throwable) =
      if (callback != null) callback(Left(t))
      else ()
  }
}

class AzureBlobstore[F[_]: ConcurrentEffect: MonadResourceErr: RaiseThrowable](
  containerURL: ContainerURL) extends Blobstore[F] {

  import AzureBlobstore._

  private val F = ConcurrentEffect[F]

  def get(path: ResourcePath): Stream[F, ByteBuffer] = {
    val bufs: F[Stream[F, ByteBuffer]] = for {
      single <- download(pathToAzurePath(path))
      r <- singleToAsync(single)
      s <- F.delay(flowableToStream(r.body(new ReliableDownloadOptions)))
    } yield s

    Stream.eval(bufs).flatten
      .handleErrorWith {
        case ex: StorageException if ex.statusCode() == 404 =>
          Stream.raiseError(ResourceError.throwableP(ResourceError.pathNotFound(path)))
      }
  }

  def isResource(path: ResourcePath): F[Boolean] = errorNotImplemented

  def list(path: ResourcePath): F[Option[Stream[F, (ResourceName, ResourcePathType)]]] = {
    val resp: F[ContainerListBlobHierarchySegmentResponse] = for {
      single <- listBlobs(None, pathToOptions(path))
      r <- singleToAsync(single)
    } yield r

    val s = Stream.eval(resp).flatMap { r =>
      val segm = r.body.segment
      val l =
        if (segm == null) List.empty
        else segm.blobItems.asScala.map(blobItemToNameType(_, path)) ++
          segm.blobPrefixes.asScala.map(blobPrefixToNameType(_, path))
      Stream.emits(l).covary[F]
    }

    s.some.pure[F]
  }

  private def blobItemToNameType(i: BlobItem, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(i.name)), ResourcePathType.LeafResource)

  private def blobPrefixToNameType(p: BlobPrefix, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(p.name)), ResourcePathType.Prefix)

  private def download(path: String): F[Single[DownloadResponse]] = {
    val blobUrl = containerURL.createBlobURL(path)
    F.delay(blobUrl.download(BlobRange.DEFAULT, BlobAccessConditions.NONE, false, Context.NONE))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def listBlobs(marker: Option[String], options: ListBlobsOptions): F[Single[ContainerListBlobHierarchySegmentResponse]] =
    F.delay(containerURL.listBlobsHierarchySegment(marker.orNull, "/", options, Context.NONE))

  private def mkAsync[A](observer: AsyncObserver[A], f: SingleObserver[A] => Unit): F[A] =
    F.async[A] { cb: (Either[Throwable, A] => Unit) =>
      observer.setCallback(cb)
      f(observer)
    }

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

  private def singleToAsync[A](single: Single[A]): F[A] =
    F.bracket(
      F.delay(new AsyncObserver[A]))(
      obs => mkAsync(obs, single.subscribe))(
      obs => F.delay(obs.dispose()))

  def flowableToStream[A](f: Flowable[A]): Stream[F, A] =
    handlerToStreamUnNoneTerminate(flowableToHandler(f))

  def flowableToHandler[A](flowable: Flowable[A]): (Either[Throwable, Option[A]] => Unit) => Unit = { cb =>
    val cons = new AsyncConsumer[A]
    cons.setCallback(cb)
    flowable.subscribe(cons.onNext, cons.onError, cons.onComplete)
  }

  def handlerToStreamUnNoneTerminate[A](
      handler: (Either[Throwable, Option[A]] => Unit) => Unit): Stream[F, A] =
    for {
      q <- Stream.eval(Queue.unbounded[F, Either[Throwable, Option[A]]])
      _ <- Stream.eval(F.delay(handler(enqueueEvent(q))))
      a <- q.dequeue.rethrow.unNoneTerminate
    } yield a

  def enqueueEvent[A](q: Queue[F, A])(event: A): Unit =
    F.runAsync(q.enqueue1(event))(_ => IO.unit).unsafeRunSync

}
