package slick

import slick.dbio.{Effect, NoStream}
import slick.jdbc.JdbcBackend
import slick.profile.{SqlAction, SqlStreamingAction}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// TODO These actions run on the new connection, so a transaction is not propagated.
object SynchronousDatabaseRunner {

  def first[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit s: JdbcBackend#Session): R = {
    val f = s.database.runInternal(action.head, true)
    Await.result(f, Duration.Inf)
  }

  def firstOption[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit s: JdbcBackend#Session): Option[R] = {
    val f = s.database.runInternal(action.headOption, true)
    Await.result(f, Duration.Inf)
  }

  def list[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit s: JdbcBackend#Session): List[R] = {
    val f = s.database.runInternal(action, true)
    Await.result(f, Duration.Inf).toList
  }

  def execute[R](action: SqlAction[R, NoStream, Effect])(implicit s: JdbcBackend#Session): R = {
    val f = s.database.runInternal(action, true)
    Await.result(f, Duration.Inf)
  }

}