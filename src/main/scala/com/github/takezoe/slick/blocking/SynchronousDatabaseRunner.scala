package com.github.takezoe.slick.blocking

import slick.basic.{BasicBackend, BasicStreamingAction}
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcBackend
import slick.sql.SqlAction

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object SynchronousDatabaseRunner {

  def first[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): R = {
    val f = runInContext(action.head)
    Await.result(f, Duration.Inf)
  }

  def firstOption[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): Option[R] = {
    val f = runInContext(action.headOption)
    Await.result(f, Duration.Inf)
  }

  def list[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E])(implicit s: JdbcBackend#Session): List[R] = {
    val f = runInContext(action)
    Await.result(f, Duration.Inf).toList
  }

  def execute[R, E <: Effect](action: SqlAction[R, NoStream, E])(implicit s: JdbcBackend#Session): R = {
    val f = runInContext(action)
    Await.result(f, Duration.Inf)
  }

  private def runInContext[R](action: DBIOAction[R, NoStream, Nothing])(implicit s: JdbcBackend#Session): Future[R] = {
    val method = classOf[BasicBackend#DatabaseDef].getDeclaredMethods.find(_.getName == "runInContext").get
    method.setAccessible(true)

    val context = new BlockingJdbcActionContext(s)
    val f = method.invoke(s.database, action, context, new java.lang.Boolean(false), new java.lang.Boolean(true))

    f.asInstanceOf[Future[R]]
  }

}