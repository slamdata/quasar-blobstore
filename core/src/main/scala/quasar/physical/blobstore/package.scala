package quasar.physical

import quasar.api.resource.ResourcePath
import quasar.connector.{Datasource, QueryResult}
import quasar.qscript.InterpretedRead

import fs2.Stream

package object blobstore {

  type DS[F[_]] = Datasource[F, Stream[F, ?], InterpretedRead[ResourcePath], QueryResult[F]]

}
