blocking-slick [![Build Status](https://travis-ci.org/takezoe/blocking-slick.svg?branch=master)](https://travis-ci.org/takezoe/blocking-slick)
==============

Provides blocking API for Slick3

Usage
-----

Add following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.github.takezoe" %% "blocking-slick" % "0.0.3"
```

You can enable blocking API by import the blocking driver as follows:

```scala
import com.github.takezoe.slick.blocking.BlockingH2Driver._
import com.github.takezoe.slick.blocking.BlockingH2Driver.blockingApi._
```

See the example of use of blocking API provided by blocking-slick:

```scala
val db = Database.forURL("jdbc:h2:mem:test")

db.withSession { implicit session =>
  // Create tables
  models.Tables.schema.create

  // Insert
  Users.insert(UsersRow(1, "takezoe"))

  // Select
  val users: Seq[UserRow] = Users.list
  
  // Select single record
  val user: UserRow = Users.filter(_.id === "takezoe".bind).first
  
  // Select single record with Option
  val user: Option[UserRow] = Users.filter(_.id === "takezoe".bind).firstOption

  // Update
  Users.filter(t => t.id === 1.bind).update(UsersRow(1, "naoki"))
  
  // Delete
  Users.filter(t => t.id === 1.bind).delete
  
  // Drop tables
  models.Tables.schema.remove
}
```

Transaction is available by using `withTransaction` instead of `withSession`:

```scala
// Transaction
db.withTransaction { implicit session =>
  ...
}
```

You can also find an example of blocking-slick with Play2 and play-slick at:
https://github.com/takezoe/blocking-slick-play2/blob/master/app/controllers/UserController.scala
