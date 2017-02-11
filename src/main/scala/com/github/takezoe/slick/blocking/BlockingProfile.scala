package com.github.takezoe.slick.blocking

import java.sql.Connection

import slick.ast.{CompiledStatement, Node, ResultSetMapping}
import slick.basic.{BasicAction, BasicStreamingAction}
import slick.dbio.SynchronousDatabaseAction
import slick.jdbc.{ActionBasedSQLInterpolation, JdbcBackend, JdbcProfile}
import slick.lifted.RunnableCompiled
import slick.relational._
import slick.util.SQLBuilder

import scala.language.existentials
import scala.language.higherKinds
import scala.language.implicitConversions

trait BlockingRelationalProfile extends RelationalProfile {
  trait BlockingAPI extends API {}
}

trait BlockingJdbcProfile extends JdbcProfile with BlockingRelationalProfile {
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
      def create(implicit s: JdbcBackend#Session): Unit = {
        createSchemaActionExtensionMethods(schema).create.asInstanceOf[SynchronousDatabaseAction[Unit, NoStream, Backend, Effect]].run(new BlockingJdbcActionContext(s))
      }
  
      def remove(implicit s: JdbcBackend#Session): Unit = {
        createSchemaActionExtensionMethods(schema).drop.asInstanceOf[SynchronousDatabaseAction[Unit, NoStream, Backend, Effect]].run(new BlockingJdbcActionContext(s))
      }
    }
  
    implicit class RepQueryExecutor[E](rep: Rep[E]){
      private val invoker = new QueryInvoker[E](queryCompiler.run(Query(rep)(slick.lifted.RepShape).toNode).tree, ())

      def run(implicit s: JdbcBackend#Session): E = invoker.first
      def selectStatement: String = invoker.selectStatement
    }
    implicit class QueryExecutor[U, C[_]](q: Query[_, U, C]) {
      private val invoker = new QueryInvoker[U](queryCompiler.run(q.toNode).tree, ())

      def run(implicit s: JdbcBackend#Session): Seq[U] = invoker.results(0).right.get.toSeq
      def selectStatement: String = invoker.selectStatement
    }

    implicit class RunnableCompiledQueryExecutor[U, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[U]]) {
      private val invoker = new QueryInvoker[U](c.compiledQuery, c.param)

      def run(implicit s: JdbcBackend#Session): Seq[U] = invoker.invoker.results(0).right.get.toSeq
      def selectStatement: String = invoker.selectStatement
    }
  
    /**
     * Extends QueryInvokerImpl to add selectStatement method.
     */
    class QueryInvoker[R](tree: Node, param: Any) extends QueryInvokerImpl[R](tree, param, null) {
      def selectStatement: String = getStatement
    }

    class BlockingQueryInvoker[U](tree: Node, param: Any){
      def selectStatement: String = {
        val invoker = new QueryInvoker[U](tree, param)
        invoker.selectStatement
      }
      def list(implicit s: JdbcBackend#Session): List[U] = {
        val invoker = new QueryInvoker[U](tree, param)
        invoker.results(0).right.get.toList
      }
  
      def first(implicit s: JdbcBackend#Session): U = {
        val invoker = new QueryInvoker[U](tree, param)
        invoker.first
      }
  
      def firstOption(implicit s: JdbcBackend#Session): Option[U] = {
        val invoker = new QueryInvoker[U](tree, param)
        invoker.firstOption
      }
    }
    implicit def queryToQueryInvoker[U, C[_]](q: Query[_ ,U, C]) = new BlockingQueryInvoker[U](queryCompiler.run(q.toNode).tree, ())
    implicit def compiledToQueryInvoker[U, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[U]]) = new BlockingQueryInvoker[U](c.compiledQuery, c.param)

    class BlockingDeleteInvoker(protected val tree: Node, param: Any) {
      def deleteStatement = createDeleteActionExtensionMethods(tree, param).delete.statements.head
  
      def delete(implicit s: JdbcBackend#Session): Int = {
        createDeleteActionExtensionMethods(tree, param).delete
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s))
      }

      def deleteInvoker: this.type = this
    }
    implicit def queryToDeleteInvoker[U, C[_]](q: Query[_ ,U, C]) = new BlockingDeleteInvoker(deleteCompiler.run(q.toNode).tree, ())
    implicit def compiledToDeleteInvoker[U, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[U]]) = new BlockingDeleteInvoker(c.compiledDelete, c.param)

    class BlockingUpdateInvoker[U](tree: Node, param: Any) {
      def updateStatement = createUpdateActionExtensionMethods(tree, param).updateStatement

      def update(value: U)(implicit s: JdbcBackend#Session): Int = {
        createUpdateActionExtensionMethods(tree, param).update(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s))
      }

      def updateInvoker: this.type = this
    }
    implicit def queryToUpdateInvoker[U, C[_]](q: Query[_ ,U, C]) = new BlockingUpdateInvoker[U](updateCompiler.run(q.toNode).tree, ())
    implicit def compiledToUpdateInvoker[U, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[U]]) = new BlockingUpdateInvoker[U](c.compiledUpdate, c.param)

    class BlockingInsertInvoker[U](compiled: CompiledInsert) {

      def +=(value: U)(implicit session: JdbcBackend#Session): Int = insert(value)
  
      def insert(value: U)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled).+=(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s))
      }
  
      def ++=(values: Iterable[U])(implicit s: JdbcBackend#Session): Int = insertAll(values.toSeq: _*)
  
      def insertAll(values: U*)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled).++=(values)
          .asInstanceOf[SynchronousDatabaseAction[Option[Int], NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s)).getOrElse(0)
      }
      
      def insertOrUpdate(value: U)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled).insertOrUpdate(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, JdbcBackend, Effect]].run(new BlockingJdbcActionContext(s))
      }

      def insertInvoker: this.type = this
    }
    implicit def queryToInsertInvoker[U, C[_]](q: Query[_ ,U, C]) = new BlockingInsertInvoker[U](compileInsert(q.toNode))
    implicit def compiledToInsertInvoker[U, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[U]]) = new BlockingInsertInvoker[U](c.compiledInsert.asInstanceOf[CompiledInsert])

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
