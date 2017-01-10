package com.github.takezoe.slick.blocking

import java.sql.Connection

import slick.SlickException
import slick.ast.{CompiledStatement, Node, ResultSetMapping}
import slick.dbio.{Effect, NoStream, SynchronousDatabaseAction}
import slick.jdbc.{ActionBasedSQLInterpolation, JdbcBackend, JdbcProfile, JdbcResultConverterDomain}
import slick.lifted.{FlatShapeLevel, Query, Rep, Shape}
import slick.relational._
import slick.sql.{SqlAction, SqlStreamingAction}
import slick.util.SQLBuilder

import scala.language.existentials
import scala.language.higherKinds
import scala.language.reflectiveCalls
import scala.language.implicitConversions

trait BlockingRelationalProfile extends RelationalProfile {
  trait BlockingAPI extends API {}
}

trait BlockingJdbcProfile extends JdbcProfile with BlockingRelationalProfile with slick.TransactionalJdbcProfile {

  val blockingApi = new BlockingAPI with ImplicitColumnTypes {}
  implicit def actionBasedSQLInterpolation(s: StringContext) = new ActionBasedSQLInterpolation(s)

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

    def remove(implicit s: JdbcBackend#Session): Unit = {
      schema.dropStatements.foreach { sql =>
        val session = s.conn.createStatement()
        try {
          session.executeUpdate(sql)
        } finally {
          session.close()
        }
      }
    }
  }

  implicit class RepQueryExecutor[E, U, R, T](rep: Rep[E])(implicit unpack: Shape[_ <: FlatShapeLevel, Rep[E], U, R]){
    private val invoker = new QueryInvoker[E](queryCompiler.run(Query(rep).toNode).tree)

    def run(implicit s: JdbcBackend#Session): E = invoker.first
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

    def list(implicit s: JdbcBackend#Session): List[U] = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.results(0).right.get.toList
    }

    def first(implicit s: JdbcBackend#Session): U = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.first
    }

    def firstOption(implicit s: JdbcBackend#Session): Option[U] = {
      val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree)
      invoker.firstOption
    }

    def delete(implicit s: JdbcBackend#Session): Int = {
      val tree = deleteCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), _) = tree
      s.withPreparedStatement(sres.sql){ st =>
        sres.setter(st, 1, null)
        st.executeUpdate
      }
    }

    def update(value: U)(implicit s: JdbcBackend#Session): Int = {
      val tree = updateCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), CompiledMapping(_converter, _)) = tree
      val converter = _converter.asInstanceOf[ResultConverter[JdbcResultConverterDomain, U]]
      s.withPreparedInsertStatement(sres.sql) { st =>
        st.clearParameters
        converter.set(value, st)
        sres.setter(st, converter.width + 1, null)
        st.executeUpdate
      }
    }

    def +=(value: U)(implicit session: JdbcBackend#Session): Int = insert(value)

    def insert(value: U)(implicit s: JdbcBackend#Session): Int = {
      val compiled = compileInsert(q.toNode)
      val a = compiled.standardInsert
      s.withPreparedStatement(a.sql) { st =>
        st.clearParameters()
        a.converter.set(value, st)
        st.executeUpdate()
      }
    }

    def ++=(values: Iterable[U])(implicit s: JdbcBackend#Session): Int = insertAll(values.toSeq: _*)

    def insertAll(values: U*)(implicit s: JdbcBackend#Session): Int = {
      def retManyBatch[U](st: java.sql.Statement, values: Iterable[U], updateCounts: Array[Int]): Int = {
        var unknown = false
        var count = 0
        for((res, idx) <- updateCounts.zipWithIndex) res match {
          case java.sql.Statement.SUCCESS_NO_INFO => unknown = true
          case java.sql.Statement.EXECUTE_FAILED => throw new SlickException("Failed to insert row #" + (idx+1))
          case i => count += i
        }
        if(unknown) 0 else count
      }

      val compiled = compileInsert(q.toNode)
      val a = compiled.standardInsert
      s.withPreparedStatement(a.sql) { st =>
        st.clearParameters()

        for(value <- values){
          a.converter.set(value, st)
          st.addBatch()
        }
        val counts = st.executeBatch()
        retManyBatch(st, values, counts)
      }
    }
  }

  implicit class ReturningInsertActionComposer2[T, R](a: ReturningInsertActionComposer[T, R]) extends JdbcBackend {

    def +=(value: T)(implicit s: JdbcBackend#Session): R = insert(value)

    def insert(value: T)(implicit s: JdbcBackend#Session): R = {
      (a += value) match {
        case a: SynchronousDatabaseAction[R, _, JdbcBackend, _] @unchecked => {
          a.run(new JdbcActionContext(){
            val useSameThread = true
            override def session: Session = s.asInstanceOf[Session]
            override def connection: Connection = s.conn
          })
        }
      }
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

  implicit class SqlStreamingActionInvoker[R](action: SqlStreamingAction[Vector[R], R, Effect]){
    def first(implicit s: JdbcBackend#Session): R = slick.SynchronousDatabaseRunner.first(action)
    def firstOption(implicit s: JdbcBackend#Session): Option[R] = slick.SynchronousDatabaseRunner.firstOption(action)
    def list(implicit s: JdbcBackend#Session): List[R] = slick.SynchronousDatabaseRunner.list(action)
  }

  implicit class SqlActionInvoker[R](action: SqlAction[R, NoStream, Effect]){
    def execute(implicit s: JdbcBackend#Session): R = slick.SynchronousDatabaseRunner.execute(action)
  }

}
