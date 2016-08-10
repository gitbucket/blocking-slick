package com.github.takezoe.slick.blocking

import org.scalatest.FunSuite
import slick.driver.H2Driver

object BlockingH2Driver extends H2Driver with SlickBlockingAPI

class SlickBlockingAPISpec extends FunSuite {

  import BlockingH2Driver._
  import BlockingH2Driver.api._
  import models.Tables._

  test("insert"){
    val db = Database.forURL("jdbc:h2:mem:test")

    db.withSession { implicit session =>
      models.Tables.schema.create

      Users.unsafeInsert(UsersRow(1, "takezoe", None))

      Users += (UsersRow(1, "takezoe", None))

      val result = Users.list
      assert(result.length == 1)
      assert(result.head == UsersRow(1, "takezoe", None))
    }
  }

}
