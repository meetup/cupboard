package com.meetup.cupboard

import java.time.Instant

import cats.data.Xor
import com.google.cloud.datastore.{Datastore, Entity, Key, ReadOption}
import com.meetup.cupboard.DatastoreFormats.{DatastoreFormat, InstantDatastoreProperty}
import shapeless.Typeable

object Cupboard {

  /**
   * Save an entity to Datastore using the default kind.
   *
   * @param ds A datastore instance
   * @param caseClass The entity to store
   * @tparam C The type of your case class
   */
  def save[C](ds: Datastore, caseClass: C)(implicit cf: DatastoreFormat[C], typeable: Typeable[C]): Result[C] = {
    save(ds, caseClass, typeable.describe)
  }

  /**
   * Save an entity with a custom kind.
   */
  def save[C](ds: Datastore, caseClass: C, kind: String)(implicit cf: DatastoreFormat[C]): Result[C] = {
    Xor.catchNonFatal {
      val key = getKey(ds, kind)
      val eBuilder = Entity.builder(key)
      val e = cf.buildEntity(caseClass, eBuilder)

      val now = Instant.now
      InstantDatastoreProperty.setEntityProperty(now, "modified", e)
      InstantDatastoreProperty.setEntityProperty(now, "created", e)

      ds.put(e.build())

      Persisted(Long.unbox(key.id()), caseClass, now, now)
    }
  }

  /**
   * Save an entity with a custom key.
   */
  def saveWithKey[C](ds: Datastore, caseClass: C, key: Key)(implicit cf: DatastoreFormat[C]): Result[C] = {
    Xor.catchNonFatal {
      val eBuilder = Entity.builder(key)
      val e = cf.buildEntity(caseClass, eBuilder)

      val now = Instant.now
      InstantDatastoreProperty.setEntityProperty(now, "modified", e)
      InstantDatastoreProperty.setEntityProperty(now, "created", e)

      ds.put(e.build())

      Persisted(Long.unbox(key.id()), caseClass, now, now)
    }
  }

  /**
   * Update an entity with a new value, using a custom kind.
   *
   * This will replace the old entity with what you're providing.
   */
  def update[C](ds: Datastore, caseClass: C, id: Long, kind: String)(implicit cf: DatastoreFormat[C]): Result[C] = {
    val key = getKeyWithId(ds, kind, id)
    saveWithKey[C](ds: Datastore, caseClass, key)
  }

  /**
   * Update an entity with a new value.
   */
  def update[C](ds: Datastore, caseClass: C, id: Long)(implicit cf: DatastoreFormat[C], typeable: Typeable[C]): Result[C] = {
    update(ds, caseClass, id, typeable.describe)
  }

  def getKey(ds: Datastore, kind: String): Key = {
    val keyFactory = ds.newKeyFactory().kind(kind)
    ds.allocateId(keyFactory.newKey())
  }

  def getKeyWithId(ds: Datastore, kind: String, id: Long): Key = {
    val keyFactory = ds.newKeyFactory().kind(kind)
    keyFactory.newKey(id)
  }

  def load[C](ds: Datastore, id: Long)(implicit cf: DatastoreFormat[C], typeable: Typeable[C]) = {
    loadKind(ds, id, typeable.describe)
  }

  def loadKind[C](ds: Datastore, id: Long, kind: String)(implicit cf: DatastoreFormat[C]): Result[C] = {
    val key = ds.newKeyFactory()
      .kind(kind)
      .newKey(id)

    val entityXor: Xor[Throwable, Entity] = Option(ds.get(key, Array.empty[ReadOption]: _*))
      .map(Xor.Right(_)) // converting option to Xor
      .getOrElse(Xor.Left(new RuntimeException(s"No entity found with id $id")))

    entityXor.flatMap { entity =>
      cf.fromEntity(entity).flatMap { caseClass =>
        val modified = InstantDatastoreProperty.getValueFromEntity("modified", entity)
        val created = InstantDatastoreProperty.getValueFromEntity("created", entity)
        modified.flatMap { mtime =>
          created.map(ctime =>
            Persisted(id, caseClass, mtime, ctime)
          )
        }
      }
    }
  }
}
