package slick

import slick.dbio.{Effect, NoStream}
import slick.jdbc.JdbcBackend
import slick.profile.{SqlAction, SqlStreamingAction}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object SynchronousDatabaseRunner {

  def first[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit session: JdbcBackend#Session): R = {
    val f = session.database.runInternal(action.head, true)
    Await.result(f, Duration.Inf)
  }

  def firstOption[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit session: JdbcBackend#Session): Option[R] = {
    val f = session.database.runInternal(action.headOption, true)
    Await.result(f, Duration.Inf)
  }

  def list[R](action: SqlStreamingAction[Vector[R], R, Effect])(implicit session: JdbcBackend#Session): List[R] = {
    val f = session.database.runInternal(action, true)
    Await.result(f, Duration.Inf).toList
  }

  def execute[R](action: SqlAction[R, NoStream, Effect])(implicit session: JdbcBackend#Session): R = {
    val f = session.database.runInternal(action, true)
    Await.result(f, Duration.Inf)
  }

}