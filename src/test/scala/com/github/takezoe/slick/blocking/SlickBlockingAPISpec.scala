package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite

class SlickBlockingAPISpec extends FunSuite {

  import BlockingH2Driver._
  import BlockingH2Driver.api._
  import models.Tables._

  test("CRUD"){
    val db = Database.forURL("jdbc:h2:mem:test")

    db.withSession { implicit session =>
      models.Tables.schema.create

      // Insert
      Users.unsafeInsert(UsersRow(1, "takezoe", None))
      Users.unsafeInsert(UsersRow(2, "shimamoto", None))
      Users.unsafeInsert(UsersRow(3, "tanacasino", None))

      val result1 = Users.sortBy(_.id).list
      assert(result1.length == 3)
      assert(result1(0) == UsersRow(1, "takezoe", None))
      assert(result1(1) == UsersRow(2, "shimamoto", None))
      assert(result1(2) == UsersRow(3, "tanacasino", None))

      // Update
      Users.filter(_.id === 1L.bind).map(_.name).unsafeUpdate("naoki")

      val result2 = Users.filter(_.id === 1L.bind).first
      assert(result2 == UsersRow(1, "naoki", None))

      // Delete
      Users.filter(_.id === 1L.bind).unsafeDelete

      val result3 = Users.filter(_.id === 1L.bind).firstOption
      assert(result3.isEmpty)
    }
  }

}
