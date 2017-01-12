package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite
import slick.jdbc.meta.MTable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class SlickBlockingAPISpec extends FunSuite {

  import BlockingH2Driver._
  // queryInsertActionExtensionMethods is conflicted with BlockingH2Driver.InsertActionExtensionMethods
  import BlockingH2Driver.blockingApi.{ queryInsertActionExtensionMethods => _, _ }
  import models.Tables._

  private val db = Database.forURL("jdbc:h2:mem:test;TRACE_LEVEL_FILE=4")

  test("CRUD operation"){
    db.withSession { implicit session =>
      models.Tables.schema.create

      // Insert
      Users.insert(UsersRow(1, "takezoe", None))
      Users.insert(UsersRow(2, "chibochibo", None))
      Users.insert(UsersRow(3, "tanacasino", None))

      val count1 = Query(Users.length).first
      assert(count1 == 3)

      val result1 = Users.sortBy(_.id).list
      assert(result1.length == 3)
      assert(result1(0) == UsersRow(1, "takezoe", None))
      assert(result1(1) == UsersRow(2, "chibochibo", None))
      assert(result1(2) == UsersRow(3, "tanacasino", None))

      // Update
      Users.filter(_.id === 1L.bind).map(_.name).update("naoki")

      val result2 = Users.filter(_.id === 1L.bind).first
      assert(result2 == UsersRow(1, "naoki", None))

      // Delete
      Users.filter(_.id === 1L.bind).delete

      val result3 = Users.filter(_.id === 1L.bind).firstOption
      assert(result3.isEmpty)

      val count2 = Query(Users.length).first
      assert(count2 == 2)

      models.Tables.schema.remove
    }
  }

  test("Plain SQL"){
    db.withSession { implicit session =>
      models.Tables.schema.create

      // plain sql
      val id1 = 1
      val name1 = "takezoe"
      val insert1 = sqlu"INSERT INTO USERS (ID, NAME) VALUES (${id1}, ${name1})"
      insert1.execute

      val query = sql"SELECT COUNT(*) FROM USERS".as[Int]
      val count1 = query.first
      assert(count1 == 1)

      val id2 = 2
      val name2 = "chibochibo"
      val insert2 = sqlu"INSERT INTO USERS (ID, NAME) VALUES (${id2}, ${name2})"
      insert2.execute

      val count2 = query.first
      assert(count2 == 2)

      models.Tables.schema.remove
    }
  }

  test("exists"){
    db.withSession { implicit session =>
      models.Tables.schema.create

      val exists1 = Users.filter(_.id === 1L.bind).filter(_.name === "takezoe".bind).exists.run
      assert(exists1 == false)

      Users.insert(UsersRow(1, "takezoe", None))

      val exists2 = Users.filter(_.id === 1L.bind).filter(_.name === "takezoe".bind).exists.run
      assert(exists2 == true)

      models.Tables.schema.remove
    }
  }

  test("insertAll"){
    db.withSession { implicit session =>
      models.Tables.schema.create

      val users = List(
        UsersRow(1, "takezoe", None),
        UsersRow(2, "chibochibo", None),
        UsersRow(3, "tanacasino", None)
      )

      Users.insertAll(users: _*)
      val count1 = Query(Users.length).first
      assert(count1 == 3)

      Users ++= users
      val count2 = Query(Users.length).first
      assert(count2 == 6)
    }
  }

  test("withTransaction"){
    db.withSession { implicit session =>
      models.Tables.schema.create

      { // rollback
        session.withTransaction {
          Users.insert(UsersRow(1, "takezoe", None))
          val exists = Users.filter(_.id === 1L.bind).exists.run
          assert(exists == true)
          session.conn.rollback()
        }
        val exists = Users.filter(_.id === 1L.bind).exists.run
        assert(exists == false)
      }

      { // ok
        session.withTransaction {
          Users.insert(UsersRow(2, "takezoe", None))
          val exists = Users.filter(_.id === 2L.bind).exists.run
          assert(exists == true)
        }
        val exists = Users.filter(_.id === 2L.bind).exists.run
        assert(exists == true)
      }

      { // nest (rollback)
        session.withTransaction {
          Users.insert(UsersRow(3, "takezoe", None))
          assert(Users.filter(_.id === 3L.bind).exists.run == true)
          session.withTransaction {
            Users.insert(UsersRow(4, "takezoe", None))
            assert(Users.filter(_.id === 4L.bind).exists.run == true)
            session.conn.rollback()
          }
        }
        assert(Users.filter(_.id === 3L.bind).exists.run == false)
        assert(Users.filter(_.id === 4L.bind).exists.run == false)
      }

      { // nest (ok)
        session.withTransaction {
          Users.insert(UsersRow(5, "takezoe", None))
          assert(Users.filter(_.id === 5L.bind).exists.run == true)
          session.withTransaction {
            Users.insert(UsersRow(6, "takezoe", None))
            assert(Users.filter(_.id === 6L.bind).exists.run == true)
          }
        }
        assert(Users.filter(_.id === 5L.bind).exists.run == true)
        assert(Users.filter(_.id === 6L.bind).exists.run == true)
      }
    }
  }
  
  test("MTable support"){
    db.withSession { implicit session =>
      models.Tables.schema.create
      
      assert(MTable.getTables.list.length == 2)
    }
  }

  test("Transaction support with Query SELECT FOR UPDATE"){
    testTransactionWithSelectForUpdate { implicit session =>
      Users.map(_.id).forUpdate.list
    }
  }

  test("Transaction support with Action SELECT FOR UPDATE"){
    testTransactionWithSelectForUpdate { implicit session =>
      sql"select id from USERS for update".as[Long].list
    }
  }
  
  private def testTransactionWithSelectForUpdate(selectForUpdate: Session => Seq[Long]) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    db.withSession { implicit session =>
      models.Tables.schema.create
      
      // Insert
      Users.insert(UsersRow(1, "takezoe", None))
      
      //concurrently do a select for update
      val f1 = Future{db.withTransaction { implicit session =>
        val l = selectForUpdate(session).length
        Thread.sleep(5000l)
        l
      }}
      
      //and try to update a row
      val f2 = Future{db.withTransaction { implicit session =>
        Thread.sleep(1000l)
        Users.filter(_.id === 1L).map(_.name).update("João")
      }}
      
      assert(Await.result(f1, Duration.Inf) == 1)
      assertThrows[Exception](Await.result(f2, Duration.Inf))
    }
  }

}
