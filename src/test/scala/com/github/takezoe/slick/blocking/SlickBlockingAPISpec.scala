package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite

class SlickBlockingAPISpec extends FunSuite {

  import BlockingH2Driver._
  import BlockingH2Driver.blockingApi._
  import models.Tables._

  test("DDL, Count and CRUD operation"){
    val db = Database.forURL("jdbc:h2:mem:test")

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

      val query = sql"SELECT COUNT(*) FROM USERS".as[Int]
      val count3 = query.first
      assert(count3 == 2)
    }
  }

}
