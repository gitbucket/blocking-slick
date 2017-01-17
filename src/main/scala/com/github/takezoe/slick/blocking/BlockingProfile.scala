package com.github.takezoe.slick.blocking

import java.sql.Connection

import slick.ast.{BaseTypedType, CompiledStatement, Node, ResultSetMapping}
import slick.basic.{BasicAction, BasicStreamingAction}
import slick.dbio.SynchronousDatabaseAction
import slick.jdbc.{ActionBasedSQLInterpolation, JdbcBackend, JdbcProfile, JdbcResultConverterDomain}
import slick.relational._
import slick.util.SQLBuilder

import scala.language.existentials
import scala.language.higherKinds
import scala.language.implicitConversions

trait BlockingRelationalProfile extends RelationalProfile {
  trait BlockingAPI extends API {}
}

trait BlockingJdbcProfile extends BlockingRelationalProfile { profile: JdbcProfile =>
  val blockingApi = new BlockingAPI {}

  trait BlockingAPI extends super.BlockingAPI with ImplicitColumnTypes with slick.JdbcProfileBlockingSession {

    implicit def actionBasedSQLInterpolation(s: StringContext): ActionBasedSQLInterpolation = new ActionBasedSQLInterpolation(s)
    private class BlockingJdbcActionContext(s: JdbcBackend#Session) extends backend.JdbcActionContext {
      val useSameThread = true
      override def session = s.asInstanceOf[backend.Session]
      override def connection: Connection = s.conn
    }

    /**
     * Extends DDL to add methods to create and drop tables immediately.
     */
    implicit class DDLInvoker(schema: DDL){
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
  
    implicit class RepQueryExecutor[E: BaseTypedType](rep: Rep[E]){
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
  
    implicit class ReturningInsertActionComposer2[T, R](a: ReturningInsertActionComposer[T, R]) {
  
      def +=(value: T)(implicit s: JdbcBackend#Session): R = insert(value)
  
      def insert(value: T)(implicit s: JdbcBackend#Session): R = {
        (a += value) match {
          case a: SynchronousDatabaseAction[R, _, JdbcBackend, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
          }
        }
      }
  
    }

    implicit class IntoInsertActionComposer2[T, R](a: IntoInsertActionComposer[T, R]) {
      def +=(value: T)(implicit s: JdbcBackend#Session): R = insert(value)
  
      def insert(value: T)(implicit s: JdbcBackend#Session): R = {
        (a += value) match {
          case a: SynchronousDatabaseAction[R, _, JdbcBackend, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
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
  
      def withTransaction[T](f: (JdbcBackend#Session) => T): T =
        withSession { s => s.withTransaction(f(s)) }
    }
  
    implicit class BasicStreamingActionInvoker[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E]){
      def first(implicit s: JdbcBackend#Session): R = {
        action.head.asInstanceOf[SynchronousDatabaseAction[R, NoStream, JdbcBackend, E]].run(new BlockingJdbcActionContext(s))
      }
      def firstOption(implicit s: JdbcBackend#Session): Option[R] = {
        action.headOption.asInstanceOf[SynchronousDatabaseAction[Option[R], NoStream, JdbcBackend, E]].run(new BlockingJdbcActionContext(s))
      }
      def list(implicit s: JdbcBackend#Session): List[R] = {
        action.asInstanceOf[SynchronousDatabaseAction[Vector[R], Streaming[R], JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s)).toList
      }
    }
  
    implicit class BasicActionInvoker[R](action: BasicAction[R, NoStream, Effect]){
      def execute(implicit s: JdbcBackend#Session): R = {
        action.asInstanceOf[SynchronousDatabaseAction[R, NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s))
      }
    }
  }
}
