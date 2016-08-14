package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite

class SlickBlockingAPISpec extends FunSuite {

  import BlockingH2Driver._
  import BlockingH2Driver.blockingApi._
  import models.Tables._

  private val db = Database.forURL("jdbc:h2:mem:test")

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

}
