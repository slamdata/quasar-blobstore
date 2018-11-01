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

import slamdata.Predef.{Array, Either, Left, Option, Right, SuppressWarnings, Throwable, Unit}

import cats.effect._
import cats.implicits._
import fs2.{RaiseThrowable, Stream}
import fs2.concurrent.Queue
import io.reactivex.disposables.Disposable
import io.reactivex.{Flowable, Single, SingleObserver}
import io.reactivex.functions.{Action, Consumer}
import io.reactivex.observers.DisposableSingleObserver

object rx {
  final class AsyncConsumer[A](cb: Either[Throwable, Option[A]] => Unit) {

    def onNext: Consumer[A] = { a => cb(Right(a.some)) }

    def onError: Consumer[Throwable] = { t => cb(Left(t)) }

    def onComplete: Action = () => { cb(Right(none)) }
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

  def flowableToStream[F[_]: ConcurrentEffect: RaiseThrowable, A](f: Flowable[A]): Stream[F, A] =
    handlerToStream(flowableToHandler(f))

  def flowableToHandler[A](flowable: Flowable[A]): (Either[Throwable, Option[A]] => Unit) => Disposable = { cb =>
    val cons = new AsyncConsumer[A](cb)
    flowable.subscribe(cons.onNext, cons.onError, cons.onComplete)
  }

  def handlerToStream[F[_]: RaiseThrowable, A](
      handler: (Either[Throwable, Option[A]] => Unit) => Disposable)(
      implicit F: ConcurrentEffect[F]): Stream[F, A] =
    for {
      q <- Stream.eval(Queue.unbounded[F, Either[Throwable, Option[A]]])
      _ <- Stream.bracket(F.delay(handler(enqueueEvent(q))))(d => F.delay(d.dispose))
      a <- q.dequeue.rethrow.unNoneTerminate
    } yield a

  def enqueueEvent[F[_]: Effect, A](q: Queue[F, A])(event: A): Unit =
    Effect[F].runAsync(q.enqueue1(event))(_ => IO.unit).unsafeRunSync

  def mkAsync[F[_], A](
      observer: AsyncObserver[A], f: SingleObserver[A] => Unit)(
      implicit F: Async[F]): F[A] =
    F.async[A] { cb: (Either[Throwable, A] => Unit) =>
      observer.setCallback(cb)
      f(observer)
    }

  def singleToAsync[F[_], A](
      single: Single[A])(
      implicit F: Async[F]): F[A] =
    F.bracket(
      F.delay(new AsyncObserver[A]))(
      obs => mkAsync[F, A](obs, single.subscribe))(
      obs => F.delay(obs.dispose()))

}
