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
import quasar.blobstore.{Blobstore, Converter}
import quasar.connector.MonadResourceErr

import java.lang.Integer
import scala.collection.JavaConverters._

import cats.effect._
import cats.implicits._
import com.microsoft.azure.storage.blob._
import com.microsoft.azure.storage.blob.models._
import fs2.{RaiseThrowable, Stream}

class AzureBlobstore[F[_]: ConcurrentEffect: MonadResourceErr: RaiseThrowable](
  containerURL: ContainerURL) extends Blobstore[F] {

  private val F = ConcurrentEffect[F]

  implicit val resourcePathToBlobURL: Converter[F, ResourcePath, BlobURL] =
    new Converter[F, ResourcePath, BlobURL] {
      override def convert(p: ResourcePath): F[BlobURL] = F.delay(pathToBlobUrl(p))
    }

  implicit val resourcePathToListBlobsOptions: Converter[F, ResourcePath, ListBlobsOptions] =
    new Converter[F, ResourcePath, ListBlobsOptions] {
      override def convert(p: ResourcePath): F[ListBlobsOptions] = pathToOptions(p).pure[F]
    }

  implicit val propsResponseToBoolean: Converter[F, BlobGetPropertiesResponse, Boolean] =
    new Converter[F, BlobGetPropertiesResponse, Boolean]{
      override def convert(r: BlobGetPropertiesResponse): F[Boolean] = true.pure[F]
    }

  implicit val listResponseToResourceNamesAndTypes: Converter[F, (ContainerListBlobHierarchySegmentResponse, ResourcePath), Option[Stream[F, (ResourceName, ResourcePathType)]]] =
    new Converter[F, (ContainerListBlobHierarchySegmentResponse, ResourcePath), Option[Stream[F, (ResourceName, ResourcePathType)]]] {
      override def convert(pair: (ContainerListBlobHierarchySegmentResponse, ResourcePath)): F[Option[Stream[F, (ResourceName, ResourcePathType)]]] =
        toResourceNamesAndTypes(pair._1, pair._2).pure[F]
    }


  private val listService = AzureListService[F, ResourcePath, (ResourceName, ResourcePathType)](containerURL, x => x)
  private val propsService = AzurePropsService[F, ResourcePath, Boolean](
    _.recover { case _: StorageException => false })

  def isResource(path: ResourcePath): F[Boolean] = propsService.props(path)

  def list(path: ResourcePath): F[Option[Stream[F, (ResourceName, ResourcePathType)]]] =
    listService.list(path)

  private def toResourceNamesAndTypes(r: ContainerListBlobHierarchySegmentResponse, path: ResourcePath)
      : Option[Stream[F, (ResourceName, ResourcePathType)]] = {
    Option(r.body.segment).map { segm =>
      val l = segm.blobItems.asScala.map(blobItemToNameType(_, path)) ++
        segm.blobPrefixes.asScala.map(blobPrefixToNameType(_, path))
      Stream.emits(l).covary[F]
    }
  }

  private def blobItemToNameType(i: BlobItem, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(i.name)), ResourcePathType.LeafResource)

  private def blobPrefixToNameType(p: BlobPrefix, path: ResourcePath): (ResourceName, ResourcePathType) =
    (ResourceName(simpleName(p.name)), ResourcePathType.Prefix)

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def normalize(name: String): String =
    if (name.endsWith("/")) normalize(name.substring(0, name.length - 1))
    else name

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def normalizePrefix(name: String): String =
    if (name.endsWith("//")) normalizePrefix(name.substring(0, name.length - 1))
    else name

  private def pathToAzurePath(path: ResourcePath): String = {
    val names = ResourcePath.resourceNamesIso.get(path).map(_.value).toList
    names.mkString("/")
  }

  private def pathToBlobUrl(path: ResourcePath): BlobURL =
    containerURL.createBlobURL(pathToAzurePath(path))

  private def pathToOptions(path: ResourcePath): ListBlobsOptions =
    new ListBlobsOptions()
      .withMaxResults(Integer.valueOf(5000))
      .withPrefix(normalizePrefix(pathToPrefix(path)))

  private def pathToPrefix(path: ResourcePath): String = {
    val names = ResourcePath.resourceNamesIso.get(path).map(_.value).toList
    val s = names.mkString("", "/", "/")
    if (s === "/") "" else s
  }

  private def simpleName(s: String): String = {
    val ns = normalize(s)
    ns.substring(ns.lastIndexOf('/') + 1)
  }
}
