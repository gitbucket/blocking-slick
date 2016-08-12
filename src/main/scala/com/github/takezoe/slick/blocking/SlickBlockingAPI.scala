package com.github.takezoe.slick.blocking

import slick.ast.{CompiledStatement, Node, ResultSetMapping}
import slick.dbio.Effect
import slick.driver.{JdbcDriver, JdbcProfile}
import slick.jdbc.{JdbcBackend, JdbcResultConverterDomain, ResultSetInvoker}
import slick.lifted.Query
import slick.profile.SqlStreamingAction
import slick.relational.{CompiledMapping, ResultConverter}
import slick.util.SQLBuilder

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.existentials
import scala.language.higherKinds
import scala.language.reflectiveCalls

trait SlickBlockingAPI extends JdbcProfile {
  self: JdbcDriver =>

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

  /**
   * Extends QueryInvokerImpl to add selectStatement method.
   */
  class QueryInvokerImpl2[R](tree: Node) extends QueryInvokerImpl[R](tree, null, null) {
    def selectStatement: String = getStatement
  }

  /**
   * Extends Query to add methods for CRUD operation.
   */
  implicit class BlockingQueryInvoker[U, C[_]](q: Query[_ ,U, C]){

    def selectStatement: String = {
      val invoker = new QueryInvokerImpl2[U](queryCompiler.run(q.toNode).tree)
      invoker.selectStatement
    }

    def deleteStatement: String = {
      val tree = deleteCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), _) = tree
      sres.sql
    }

    def list(implicit session: JdbcBackend#Session): List[U] = {
      val invoker = new QueryInvokerImpl2[U](queryCompiler.run(q.toNode).tree)
      invoker.results(0).right.get.toList
    }

    def first(implicit session: JdbcBackend#Session): U = {
      val invoker = new QueryInvokerImpl2[U](queryCompiler.run(q.toNode).tree)
      invoker.first
    }

    def firstOption(implicit session: JdbcBackend#Session): Option[U] = {
      val invoker = new QueryInvokerImpl2[U](queryCompiler.run(q.toNode).tree)
      invoker.firstOption
    }

    def unsafeDelete(implicit session: JdbcBackend#Session): Int = {
      val tree = deleteCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), _) = tree
      session.withPreparedStatement(sres.sql){ st =>
        sres.setter(st, 1, null)
        st.executeUpdate
      }
    }

    def unsafeUpdate(value: U)(implicit session: JdbcBackend#Session): Int = {
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

//    def returningId[RU](returning: Query[_, RU, C]): ReturningInsertInvoker[RU] = {
//      new ReturningInsertInvoker[RU](returning.toNode)
//    }
//
//    class ReturningInsertInvoker[RU](keys: Node) {
//
//      protected val compiled = compileInsert(q.toNode)
//      protected val (_, keyConverter, _) = compiled.buildReturnColumns(keys)
//      protected val converter = keyConverter.asInstanceOf[ResultConverter[JdbcResultConverterDomain, RU]]
//
//      def unsafeInsert(value: U)(implicit session: JdbcBackend#Session): RU = {
//        val compiled = compileInsert(q.toNode)
//        val a = compiled.standardInsert
//        session.withPreparedStatement(a.sql) { st =>
//          st.clearParameters()
//          a.converter.set(value, st)
//          st.executeUpdate()
//          ResultSetInvoker[RU](_ => st.getGeneratedKeys)(pr => converter.read(pr.rs)).first
//        }
//      }
//    }

    def unsafeInsert(value: U)(implicit session: JdbcBackend#Session): Int = {
      val compiled = compileInsert(q.toNode)
      val a = compiled.standardInsert
      session.withPreparedStatement(a.sql) { st =>
        st.clearParameters()
        a.converter.set(value, st)
        st.executeUpdate()
      }
    }

    def insertAll(values: U*)(implicit session: JdbcBackend#Session): Int = {
      values.map { value => unsafeInsert(value) }.sum
    }
  }


  implicit class ReturningInsertActionComposer2[T, R](a: ReturningInsertActionComposer[T, R]){
    def unsafeInsert(value: T)(implicit session: JdbcBackend#Session): R = {
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
