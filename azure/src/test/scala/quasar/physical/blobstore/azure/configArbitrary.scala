/*
 * Copyright 2014–2019 SlamData Inc.
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

package quasar.physical.blobstore.azure

import slamdata.Predef._
import quasar.blobstore.azure._
import quasar.connector.{DataFormat => DF}

import org.scalacheck._, Arbitrary._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.numeric._

object configArbitrary {

  val genCredentials: Gen[AzureCredentials] = for {
    n <- arbitrary[String].map(AccountName)
    k <- arbitrary[String].map(AccountKey)
  } yield AzureCredentials(n, k)

  val genMaxQueueSize: Gen[MaxQueueSize] =
    arbitrary[Int Refined Positive].map(MaxQueueSize(_))

  val genAzureConfig: Gen[AzureConfig] = for {
    c <- arbitrary[String].map(ContainerName)
    cred <- Gen.option(genCredentials)
    s <- arbitrary[String].map(StorageUrl)
    qs <- Gen.option(genMaxQueueSize)
    rt <- Gen.oneOf(
      DF.ldjson,
      DF.json,
      DF.compressed(DF.json),
      DF.compressed(DF.ldjson))
  } yield AzureConfig(c, cred, s, qs, rt)

  implicit val arbAzureConfig: Arbitrary[AzureConfig] =
    Arbitrary(genAzureConfig)

}
