blocking-slick [![Scala CI](https://github.com/takezoe/blocking-slick/actions/workflows/scala.yml/badge.svg)](https://github.com/takezoe/blocking-slick/actions/workflows/scala.yml)
==============

Provides Slick2 compatible blocking API for Slick3.

Setup
-----

Add following dependency to your `build.sbt`:

```scala
// for Slick 3.3
libraryDependencies += "com.github.takezoe" %% "blocking-slick-33" % "0.0.13"

// for Slick 3.2
libraryDependencies += "com.github.takezoe" %% "blocking-slick-32" % "0.0.11"

// for Slick 3.1
libraryDependencies += "com.github.takezoe" %% "blocking-slick-31" % "0.0.7"
```

You can enable blocking API by import the blocking driver as follows:

```scala
import com.github.takezoe.slick.blocking.BlockingH2Driver.blockingApi._
```

Slick2 style blocking API
----

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

Plain sql can be executed synchronously as well.

```scala
val id = 1
val name = "takezoe"
val insert = sqlu"INSERT INTO USERS (ID, NAME) VALUES (${id1}, ${name1})"
insert.execute
```

Transaction is available by using `withTransaction` instead of `withSession`:

```scala
// Transaction
db.withTransaction { implicit session =>
  ...
}
```

DBIO support
----

blocking-slick also provides a way to run `DBIO` synchronously. It would help to rewrite Slick2 style code to Slick3 style code gradually.

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

Resources
----

You can see actual codes in [the testcase](https://github.com/takezoe/blocking-slick/blob/master/src/test/scala/com/github/takezoe/slick/blocking/SlickBlockingAPISpec.scala), and also a blocking-slick with Play2 and play-slick example is available at [here](https://github.com/takezoe/blocking-slick-play2).
