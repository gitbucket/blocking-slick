blocking-slick
==============

Provides blocking API for Slick3

Usage
-----

Add following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.github.takezoe" %% "blocking-slick" % "0.0.1"
```

Define an object like follows:
                                      
```scala
package myapp.slick.driver

import slick.driver.H2Driver
import com.github.takezoe.slick.blocking.SlickBlockingAPI

object BlockingH2Driver extends H2Driver with SlickBlockingAPI
```

Then, you can enable blocking API by import this object as follows:

```scala
import myapp.slick.driver.BlockingH2Driver._
import myapp.slick.driver.BlockingH2Driver.api._
```

See the example of use of blocking API:

```scala
val db = Database.forURL("jdbc:h2:mem:test")

db.withSession { implicit session =>
  // Create tables
  models.Tables.schema.create

  // Insert
  Users.unsafeInsert(UsersRow(1, "takezoe"))

  // Select
  val users: Seq[UserRow] = Users.list
  
  // Select single record
  val user: UserRow = Users.filter(_.id === "takezoe".bind).first
  
  // Select single record with Option
  val user: Option[UserRow] = Users.filter(_.id === "takezoe".bind).firstOption

  // Update
  Users.filter(t => t.id === 1.bind).unsafeUpdate(UsersRow(1, "naoki"))
  
  // Delete
  Users.filter(t => t.id === 1.bind).unsafeDelete
  
  // Drop tables
  models.Tables.schema.remove
}

// Transaction
db.withTransaction { implicit session =>
  ...
}
```

You can also find an example of blocking-slick with Play2 and play-slick at:
https://github.com/takezoe/blocking-slick-play2/blob/master/app/controllers/UserController.scala
