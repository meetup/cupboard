package com.meetup.cupboard.tests

import java.time.{Period, ZoneOffset, ZonedDateTime}

import org.scalatest._
import com.meetup.cupboard.models.{Bar, Subscription, _}
import cats.data.Xor
import com.google.cloud.datastore.Datastore
import com.meetup.cupboard.{AdHocDatastore, Cupboard}
import com.meetup.cupboard.Cupboard
import com.meetup.cupboard.datastore.shapeless.DatastoreFormats._
import com.meetup.cupboard.DatastoreFormat
import shapeless.Typeable

import scala.reflect.ClassTag

class DatastoreSpec extends FunSpec with Matchers with AdHocDatastore {
  describe("DatastoreFormats") {
    val z = Foo("hi", 3, true)

    it("should serialize and deserialize simple case classes") {
      withDatastore() { ds =>

        val z1Result = Cupboard.save(ds, z, "customKind")

        val z1P = z1Result.getOrElse(fail())
        val z1R = Cupboard.loadKind[Foo](ds, z1P.id, "customKind")
        z1Result shouldBe z1R
        z1R.map(_.entity) shouldBe Xor.Right(z)

        testSaveAndLoad(ds, BigDecimalTest(BigDecimal(392.23)))

      }
    }

    it("should support custom kinds") {
      withDatastore() { ds =>

        val z2Result = Cupboard.save(ds, z)

        val z2P = z2Result.getOrElse(fail())

        val z2R = Cupboard.loadKind[Foo](ds, z2P.id, "Foo")
        z2Result shouldBe z2R
        z2R.map(_.entity) shouldBe Xor.Right(z)

        val bar = Bar(1, Foo("hi", 4, false))
        val barResult = Cupboard.save(ds, bar)
        val barP = barResult.getOrElse(fail())

        val barR = Cupboard.load[Bar](ds, barP.id)
        barResult shouldBe barR
        barR.map(_.entity) shouldBe Xor.Right(bar)
      }
    }

    it("should support Option") {
      withDatastore() { ds =>
        val e = Entitlements(Some(100), None)
        val eResult = Cupboard.save(ds, e)
        val eP = eResult.getOrElse(fail())
        val eR = Cupboard.load[Entitlements](ds, eP.id)
        eResult shouldBe eR

        val e2 = Entitlements(None, Some(400))
        val e2Result = Cupboard.update(ds, e2, eP.id)
        val e2P = e2Result.getOrElse(fail())

        val e2R = Cupboard.load[Entitlements](ds, eP.id)

        e2R.toOption match {
          case Some(p) => {
            p.entity shouldBe e2P.entity
            p.created shouldBe e2P.created
            // p.modified should not be e2P.modified
          }
          case None => fail()
        }
      }
    }

    it("should support classes w/ sequences of a case class as a property") {
      withDatastore() { ds =>
        val many = Many(List(Simple("hello"), Simple("world"), Simple("foo")))
        testSaveAndLoad(ds, many)

        val seqString = SeqStringTest(Seq("hi", "world", "foo"))
        testSaveAndLoad(ds, seqString)

        val seqInt = SeqIntTest(Seq(1, 2, 3))
        testSaveAndLoad(ds, seqInt)

      }
    }

    it("should support classes w/ Period properties") {
      withDatastore() { ds =>
        val trialPeriod = TrialPeriod(Period.of(1, 2, 3))
        testSaveAndLoad(ds, trialPeriod)
      }
    }

    it("should support classes w/ ZonedDateTime properties") {
      withDatastore() { ds =>
        val now = ZonedDateTime.now()
        val zdtt = ZonedDateTimeTest(now)
        testSaveAndLoad(ds, zdtt)
      }
    }

    it("should support sealed families of case classes") {
      withDatastore() { ds =>
        testSaveAndLoad[SubscriptionStatus](ds, SubscriptionStatus.Expired)
        testSaveAndLoad[RenewalDuration](ds, RenewalDuration.MonthlyRenewal)
      }
    }

    it("should support sealed families as individual properties") {
      withDatastore() { ds =>
        val zdt = ZonedDateTime.now()
        val now = ZonedDateTime.from(zdt.toInstant().atOffset(ZoneOffset.UTC))

        val subscription = Subscription.empty.copy(
          startDate = Some(now),
          renewDate = Some(now),
          status = SubscriptionStatus.Active)

        testSaveAndLoad[Subscription](ds, subscription)

      }
    }

    it("should support custom keys") {
      withDatastore() { ds =>
        val z = Foo("hi", 3, true)
        val key = Cupboard.getKey(ds, "Foo")
        val zResult = Cupboard.saveWithKey[Foo](ds, z, key)
        assert(zResult.isRight)
        val zP = zResult.getOrElse(fail())
        val z2R = Cupboard.loadKind[Foo](ds, zP.id, "Foo")
        zResult shouldBe z2R
        z2R.map(_.entity) shouldBe Xor.Right(z)
      }
    }

  }

  /**
   * Utility function that persists and then loads a case class.
   *
   * It asserts that the persisted and restored classes are the same.
   *
   * @param ds Datastore
   * @param c  case class to be persisted
   * @param cf implicit DatastoreFormat
   * @tparam C type of case class
   */
  def testSaveAndLoad[C](ds: Datastore, c: C)(implicit cf: DatastoreFormat[C], tag: ClassTag[C], typeable: Typeable[C]) = {
    val cResult = Cupboard.save[C](ds, c)
    val cPersisted = cResult.getOrElse(fail())
    val cRestored = Cupboard.load[C](ds, cPersisted.id)
    cRestored shouldBe cResult
    cPersisted.entity shouldBe c
  }
}
