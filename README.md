blocking-slick [![Build Status](https://travis-ci.org/takezoe/blocking-slick.svg?branch=master)](https://travis-ci.org/takezoe/blocking-slick)
==============

Provides Slick2 compatible blocking API for Slick3.

Usage
-----

Add following dependency to your `build.sbt`:

```scala
// for Slick 3.2
libraryDependencies += "com.github.takezoe" %% "blocking-slick-32" % "0.0.10"

// for Slick 3.1
libraryDependencies += "com.github.takezoe" %% "blocking-slick-31" % "0.0.7"
```

You can enable blocking API by import the blocking driver as follows:

```scala
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
  
  // Insert returning new id
  val newID: Long = (Users returning Users.map(_.id)).insert(UsersRow(1, "takezoe"))

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

Plain sql can be executed synchronously as well, even when composed together

```scala
db.withSession { implicit session =>
  val id1 = 1
  val id2 = 2
  val name1 = "takezoe"
  val name2 = "chibochibo"
  val insert1 = sqlu"INSERT INTO USERS (ID, NAME) VALUES (${id1}, ${name1})" andThen
                sqlu"INSERT INTO USERS (ID, NAME) VALUES (${id2}, ${name2})"
  insert1.run

  val query = for {
    count <- sql"SELECT COUNT(*) FROM USERS".as[Int].head
    max   <- sql"SELECT MAX(ID) FROM USERS".as[Int].head
  } yield (count, max)
  val (count1, max1) = query.run
  assert(count1 == 2)
  assert(max1 == 2)
}
```

Note that using `flatMap` and `andThen` requires an `ExecutionContext`, but if you run that code synchronously that value will be ignored.

Transaction is available by using `withTransaction` instead of `withSession`:

```scala
// Transaction
db.withTransaction { implicit session =>
  ...
}
```

You can also find an example of blocking-slick with Play2 and play-slick at:
https://github.com/takezoe/blocking-slick-play2/blob/master/app/controllers/UserController.scala
