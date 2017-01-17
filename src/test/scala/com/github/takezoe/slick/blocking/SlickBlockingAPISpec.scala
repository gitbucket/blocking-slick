package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite
import slick.jdbc.meta.MTable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class SlickBlockingAPISpec extends FunSuite {

  object Tables extends {
    val profile = BlockingH2Driver
  } with models.Tables
  import BlockingH2Driver.blockingApi._
  import Tables._

  private val db = Database.forURL("jdbc:h2:mem:test;TRACE_LEVEL_FILE=4")

  test("CRUD operation"){
    db.withSession { implicit session =>
      Tables.schema.create

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

      Tables.schema.remove
    }
  }

  test("Plain SQL"){
    db.withSession { implicit session =>
      Tables.schema.create

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

      Tables.schema.remove
    }
  }

  test("exists"){
    db.withSession { implicit session =>
      Tables.schema.create

      val exists1 = Users.filter(_.id === 1L.bind).filter(_.name === "takezoe".bind).exists.run
      assert(exists1 == false)

      Users.insert(UsersRow(1, "takezoe", None))

      val exists2 = Users.filter(_.id === 1L.bind).filter(_.name === "takezoe".bind).exists.run
      assert(exists2 == true)

      Tables.schema.remove
    }
  }

  test("insertAll"){
    db.withSession { implicit session =>
      Tables.schema.create

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

  test("insert returning"){
    db.withSession { implicit session =>
      Tables.schema.create
      
      val id = Users.returning(Users.map(_.id)) insert UsersRow(1, "takezoe", None)
      assert(id == 1)
      assert(Users.length.run == 1)
      val u = (Users.returning(Users.map(_.id)).into((u, id) => u.copy(id = id))) insert UsersRow(2, "takezoe", None)
      assert(u.id == 2)
      assert(Users.length.run == 2)
    }
    
  }

  test("withTransaction Query"){
    withTransaction(
       u => s => Users.insert(u)(s),
      id => s => Users.filter(_.id === id.bind).exists.run(s)
    )
  }
  
  test("withTransaction Action"){
    withTransaction(
       u => s => sqlu"insert into users values (${u.id}, ${u.name}, ${u.companyId})".execute(s),
      id => s => sql"select exists (select * from users where id = $id)".as[Boolean].first(s)
    )
  }
  
  private def withTransaction(
    insertUser: UsersRow => Session => Int,
    existsUser: Long => Session => Boolean
  ) = {
    db.withSession { implicit session =>
      Tables.schema.create

      { // rollback
        session.withTransaction {
          insertUser(UsersRow(1, "takezoe", None))(session)
          val exists = existsUser(1)(session)
          assert(exists == true)
          session.conn.rollback()
        }
        val exists = existsUser(1)(session)
        assert(exists == false)
      }

      { // ok
        session.withTransaction {
          insertUser(UsersRow(2, "takezoe", None))(session)
          val exists = existsUser(2)(session)
          assert(exists == true)
        }
        val exists = existsUser(2)(session)
        assert(exists == true)
      }

      { // nest (rollback)
        session.withTransaction {
          insertUser(UsersRow(3, "takezoe", None))(session)
          assert(existsUser(3)(session) == true)
          session.withTransaction {
            insertUser(UsersRow(4, "takezoe", None))(session)
            assert(existsUser(4)(session) == true)
            session.conn.rollback()
          }
        }
        assert(existsUser(3)(session) == false)
        assert(existsUser(4)(session)== false)
      }

      { // nest (ok)
        session.withTransaction {
          insertUser(UsersRow(5, "takezoe", None))(session)
          assert(existsUser(5)(session) == true)
          session.withTransaction {
            insertUser(UsersRow(6, "takezoe", None))(session)
            assert(existsUser(6)(session) == true)
          }
        }
        assert(existsUser(5)(session) == true)
        assert(existsUser(6)(session) == true)
      }
    }
  }
  
  test("MTable support"){
    db.withSession { implicit session =>
      Tables.schema.create
      
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
      Tables.schema.create
      
      // Insert
      Users.insert(UsersRow(1, "takezoe", None))
      
      //concurrently do a select for update
      val f1 = Future{db.withTransaction { implicit session =>
        val l = selectForUpdate(session).length
        //default h2 lock timeout is 1000ms
        Thread.sleep(3000l)
        l
      }}
      
      //and try to update a row
      val f2 = Future{db.withTransaction { implicit session =>
        Thread.sleep(500l)
        Users.filter(_.id === 1L).map(_.name).update("João")
      }}
      
      assert(Await.result(f1, Duration.Inf) == 1)
      assertThrows[Exception](Await.result(f2, Duration.Inf))
    }
  }

}
