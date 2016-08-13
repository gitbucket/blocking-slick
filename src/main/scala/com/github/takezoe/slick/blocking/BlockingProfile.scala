package com.github.takezoe.slick.blocking

import slick.ast.{CompiledStatement, Node, ResultSetMapping}
import slick.dbio.Effect
import slick.driver.{JdbcDriver, JdbcProfile}
import slick.jdbc.{ActionBasedSQLInterpolation, JdbcBackend, JdbcResultConverterDomain, ResultSetInvoker}
import slick.lifted.{Query, Rep}
import slick.profile._
import slick.relational.{CompiledMapping, ResultConverter}
import slick.util.SQLBuilder

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.existentials
import scala.language.higherKinds
import scala.language.reflectiveCalls
import scala.language.implicitConversions

trait BlockingRelationalProfile extends RelationalProfile {
  self: RelationalDriver =>
  trait BlockingAPI extends API {}
}

trait BlockingJdbcProfile extends JdbcProfile with BlockingRelationalProfile {
  self: JdbcDriver =>

  val blockingApi = new BlockingAPI with ImplicitColumnTypes {}
  implicit def actionBasedSQLInterpolation(s: StringContext) = new ActionBasedSQLInterpolation(s)
  implicit def repToQueryExecutor[U](rep: Rep[U]): RepQueryExecutor[U] = new RepQueryExecutor(rep)

  /**
   * Extends DDL to add methods to create and drop tables immediately.
   */
  implicit class DDLInvoker(schema: {
    def createStatements: Iterator[String]
    def dropStatements: Iterator[String]
  }){
    def create(implicit session: JdbcBackend#Session): Unit = {
      schema.createStatements.foreach { sql =>
        val s = session.conn.createStatement()
        try {
          s.executeUpdate(sql)
        } finally {
          s.close()
        }
      }
    }

    def remove(implicit session: JdbcBackend#Session): Unit = {
      schema.dropStatements.foreach { sql =>
        val s = session.conn.createStatement()
        try {
          s.executeUpdate(sql)
        } finally {
          s.close()
        }
      }
    }
  }

  class RepQueryExecutor[U](rep: Rep[U]){
    private val tree = queryCompiler.run(rep.toNode).tree
    private val invoker = new QueryInvoker[U](tree)

    def run(implicit session: JdbcBackend#Session): U = invoker.first
    def selectStatement: String = invoker.selectStatement
  }

  /**
   * Extends QueryInvokerImpl to add selectStatement method.
   */
  class QueryInvoker[R](tree: Node) extends QueryInvokerImpl[R](tree, null, null) {
    def selectStatement: String = getStatement
  }

  /**
   * Extends Query to add methods for CRUD operation.
   */
  implicit class BlockingQueryInvoker[U, C[_]](q: Query[_ ,U, C]){

    def selectStatement: String = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.selectStatement
    }

    def deleteStatement: String = {
      val tree = deleteCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), _) = tree
      sres.sql
    }

    def list(implicit session: JdbcBackend#Session): List[U] = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.results(0).right.get.toList
    }

    def first(implicit session: JdbcBackend#Session): U = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.first
    }

    def firstOption(implicit session: JdbcBackend#Session): Option[U] = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.firstOption
    }

    def delete(implicit session: JdbcBackend#Session): Int = {
      val tree = deleteCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), _) = tree
      session.withPreparedStatement(sres.sql){ st =>
        sres.setter(st, 1, null)
        st.executeUpdate
      }
    }

    def update(value: U)(implicit session: JdbcBackend#Session): Int = {
      val tree = updateCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), CompiledMapping(_converter, _)) = tree
      val converter = _converter.asInstanceOf[ResultConverter[JdbcResultConverterDomain, U]]
      session.withPreparedInsertStatement(sres.sql) { st =>
        st.clearParameters
        converter.set(value, st)
        sres.setter(st, converter.width + 1, null)
        st.executeUpdate
      }
    }

    def +=(value: U)(implicit session: JdbcBackend#Session): Int = insert(value)

    def insert(value: U)(implicit session: JdbcBackend#Session): Int = {
      val compiled = compileInsert(q.toNode)
      val a = compiled.standardInsert
      session.withPreparedStatement(a.sql) { st =>
        st.clearParameters()
        a.converter.set(value, st)
        st.executeUpdate()
      }
    }

    def ++=(values: U*)(implicit session: JdbcBackend#Session): Int = insertAll(values: _*)

    // TODO should be batch insert
    def insertAll(values: U*)(implicit session: JdbcBackend#Session): Int = {
      values.map { value => insert(value) }.sum
    }
  }

  // TODO should not be use DBIO and Future
  implicit class ReturningInsertActionComposer2[T, R](a: ReturningInsertActionComposer[T, R]){

    def +=(value: T)(implicit session: JdbcBackend#Session): R = insert(value)

    def insert(value: T)(implicit session: JdbcBackend#Session): R = {
      val f = session.database.run(a += value)
      Await.result(f, Duration.Inf)
    }

  }

  /**
   * Extends Database to add methods for session management.
   */
  implicit class BlockingDatabase(db: JdbcBackend#DatabaseDef) {

    def withSession[T](f: (JdbcBackend#Session) => T): T = {
      val session = db.createSession()
      try {
        f(session)
      } finally {
        session.close()
      }
    }

    def withTransaction[T](f: (JdbcBackend#Session) => T): T = {
      val session = db.createSession()
      session.conn.setAutoCommit(false)
      try {
        val result = f(session)
        session.conn.commit()
        result
      } catch {
        case e: Exception =>
          session.conn.rollback()
          throw e
      } finally {
        session.close()
      }
    }

  }

  // TODO should not be use DBIO and Future
  implicit class SqlStreamingActionInvoker[R](a: SqlStreamingAction[Vector[R], R, Effect]){

    def first(implicit session: JdbcBackend#Session): R = {
      val f = session.database.run(a.head)
      Await.result(f, Duration.Inf)
    }

    def firstOption(implicit session: JdbcBackend#Session): Option[R] = {
      val f = session.database.run(a.headOption)
      Await.result(f, Duration.Inf)
    }

    def list(implicit session: JdbcBackend#Session): List[R] = {
      val f = session.database.run(a)
      Await.result(f, Duration.Inf).toList
    }
  }

}
