package slick

import slick.basic.BasicStreamingAction
import slick.dbio.{Effect, NoStream}
import slick.jdbc.JdbcBackend
import slick.sql.SqlAction

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// TODO These actions run on the new connection, so a transaction is not propagated.
object SynchronousDatabaseRunner {

  def first[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): R = {
    val f = s.database.runInternal(action.head, true)
    Await.result(f, Duration.Inf)
  }

  def firstOption[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): Option[R] = {
    val f = s.database.runInternal(action.headOption, true)
    Await.result(f, Duration.Inf)
  }

  def list[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): List[R] = {
    val f = s.database.runInternal(action, true)
    Await.result(f, Duration.Inf).toList
  }

  def execute[R, E <: Effect](action: SqlAction[R, NoStream, E])(implicit s: JdbcBackend#Session): R = {
    val f = s.database.runInternal(action, true)
    Await.result(f, Duration.Inf)
  }

}