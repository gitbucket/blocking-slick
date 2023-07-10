package com.github.takezoe.slick.blocking

import java.sql.Connection
import slick.ast.Node
import slick.basic.BasicAction
import slick.basic.BasicStreamingAction
import slick.dbio._
import slick.jdbc.ActionBasedSQLInterpolation
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile
import slick.lifted.RunnableCompiled
import slick.relational._
import scala.language.existentials
import scala.language.implicitConversions

trait BlockingRelationalProfile extends RelationalProfile {
  trait BlockingAPI extends RelationalAPI {}
}

trait BlockingJdbcProfile extends JdbcProfile with BlockingRelationalProfile {
  val blockingApi = new BlockingJdbcAPI {}

  trait BlockingJdbcAPI extends BlockingAPI with JdbcImplicitColumnTypes with slick.JdbcProfileBlockingSession {

    implicit def actionBasedSQLInterpolation(s: StringContext): ActionBasedSQLInterpolation =
      new ActionBasedSQLInterpolation(s)
    private class BlockingJdbcActionContext(s: JdbcBackend#Session) extends backend.JdbcActionContext {
      val useSameThread = true
      override def session = s.asInstanceOf[backend.Session]
      override def connection: Connection = s.conn
    }

    /**
     * Extends DDL to add methods to create and drop tables immediately.
     */
    implicit class DDLInvoker(schema: DDL) {
      def create(implicit s: JdbcBackend#Session): Unit = {
        createSchemaActionExtensionMethods(schema).create
          .asInstanceOf[SynchronousDatabaseAction[Unit, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }

      def remove(implicit s: JdbcBackend#Session): Unit = {
        createSchemaActionExtensionMethods(schema).drop
          .asInstanceOf[SynchronousDatabaseAction[Unit, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }
    }

    implicit class RepQueryExecutor[E](rep: Rep[E]) {
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

    class BlockingQueryInvoker[U](tree: Node, param: Any) {
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
    implicit def queryToQueryInvoker[U, C[_]](q: Query[_, U, C]): BlockingQueryInvoker[U] =
      new BlockingQueryInvoker[U](queryCompiler.run(q.toNode).tree, ())
    implicit def compiledToQueryInvoker[U, C[_]](
      c: RunnableCompiled[_ <: Query[_, _, C], C[U]]
    ): BlockingQueryInvoker[U] =
      new BlockingQueryInvoker[U](c.compiledQuery, c.param)

    class BlockingDeleteInvoker(protected val tree: Node, param: Any) {
      def deleteStatement = createDeleteActionExtensionMethods(tree, param).delete.statements.head

      def delete(implicit s: JdbcBackend#Session): Int = {
        createDeleteActionExtensionMethods(tree, param).delete
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }

      def deleteInvoker: this.type = this
    }
    implicit def queryToDeleteInvoker[U, C[_]](q: Query[_, U, C]): BlockingDeleteInvoker =
      new BlockingDeleteInvoker(deleteCompiler.run(q.toNode).tree, ())
    implicit def compiledToDeleteInvoker[U, C[_]](
      c: RunnableCompiled[_ <: Query[_, _, C], C[U]]
    ): BlockingDeleteInvoker =
      new BlockingDeleteInvoker(c.compiledDelete, c.param)

    class BlockingUpdateInvoker[U](tree: Node, param: Any) {
      def updateStatement = createUpdateActionExtensionMethods(tree, param).updateStatement

      def update(value: U)(implicit s: JdbcBackend#Session): Int = {
        createUpdateActionExtensionMethods(tree, param)
          .update(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }

      def updateInvoker: this.type = this
    }
    implicit def queryToUpdateInvoker[U, C[_]](q: Query[_, U, C]): BlockingUpdateInvoker[U] =
      new BlockingUpdateInvoker[U](updateCompiler.run(q.toNode).tree, ())
    implicit def compiledToUpdateInvoker[U, C[_]](
      c: RunnableCompiled[_ <: Query[_, _, C], C[U]]
    ): BlockingUpdateInvoker[U] =
      new BlockingUpdateInvoker[U](c.compiledUpdate, c.param)

    class BlockingInsertInvoker[U](compiled: CompiledInsert) {

      def +=(value: U)(implicit session: JdbcBackend#Session): Int = insert(value)

      def insert(value: U)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled)
          .+=(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }

      def ++=(values: Iterable[U])(implicit s: JdbcBackend#Session): Int = insertAll(values.toSeq: _*)

      def insertAll(values: U*)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled)
          .++=(values)
          .asInstanceOf[SynchronousDatabaseAction[Option[Int], NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
          .getOrElse(0)
      }

      def insertOrUpdate(value: U)(implicit s: JdbcBackend#Session): Int = {
        createInsertActionExtensionMethods(compiled)
          .insertOrUpdate(value)
          .asInstanceOf[SynchronousDatabaseAction[Int, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }

      def insertInvoker: this.type = this
    }
    implicit def queryToInsertInvoker[U, C[_]](q: Query[_, U, C]): BlockingInsertInvoker[U] =
      new BlockingInsertInvoker[U](compileInsert(q.toNode))
    implicit def compiledToInsertInvoker[U, C[_]](
      c: RunnableCompiled[_ <: Query[_, _, C], C[U]]
    ): BlockingInsertInvoker[U] =
      new BlockingInsertInvoker[U](c.compiledInsert.asInstanceOf[CompiledInsert])

    implicit class ReturningInsertActionComposer2[T, R](a: ReturningInsertActionComposer[T, R]) {

      def +=(value: T)(implicit s: JdbcBackend#Session): R = insert(value)

      def insert(value: T)(implicit s: JdbcBackend#Session): R = {
        (a += value) match {
          case a: SynchronousDatabaseAction[R, _, BlockingJdbcActionContext, ?, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
          }
        }
      }

      def ++=(values: Iterable[T])(implicit s: JdbcBackend#Session): Seq[R] = insertAll(values.toSeq: _*)

      def insertAll(values: T*)(implicit s: JdbcBackend#Session): Seq[R] = {
        (a ++= values) match {
          case a: SynchronousDatabaseAction[Seq[R], _, BlockingJdbcActionContext, ?, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
          }
        }
      }

    }

    implicit class IntoInsertActionComposer2[T, R](a: IntoInsertActionComposer[T, R]) {
      def +=(value: T)(implicit s: JdbcBackend#Session): R = insert(value)

      def insert(value: T)(implicit s: JdbcBackend#Session): R = {
        (a += value) match {
          case a: SynchronousDatabaseAction[R, _, BlockingJdbcActionContext, ?, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
          }
        }
      }

      def ++=(values: Iterable[T])(implicit s: JdbcBackend#Session): Seq[R] = insertAll(values.toSeq: _*)

      def insertAll(values: T*)(implicit s: JdbcBackend#Session): Seq[R] = {
        (a ++= values) match {
          case a: SynchronousDatabaseAction[Seq[R], _, BlockingJdbcActionContext, ?, _] @unchecked => {
            a.run(new BlockingJdbcActionContext(s))
          }
        }
      }

    }

    /**
     * Extends Database to add methods for session management.
     */
    implicit class BlockingDatabase(db: JdbcBackend#JdbcDatabaseDef) {

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

    implicit class BasicStreamingActionInvoker[R, E <: Effect](action: BasicStreamingAction[Vector[R], R, E]) {
      def first(implicit s: JdbcBackend#Session): R = {
        action.head
          .asInstanceOf[SynchronousDatabaseAction[R, NoStream, BlockingJdbcActionContext, ?, E]]
          .run(new BlockingJdbcActionContext(s))
      }
      def firstOption(implicit s: JdbcBackend#Session): Option[R] = {
        action.headOption
          .asInstanceOf[SynchronousDatabaseAction[Option[R], NoStream, BlockingJdbcActionContext, ?, E]]
          .run(new BlockingJdbcActionContext(s))
      }
      def list(implicit s: JdbcBackend#Session): List[R] = {
        action
          .asInstanceOf[SynchronousDatabaseAction[Vector[R], Streaming[R], BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
          .toList
      }
    }

    implicit class BasicActionInvoker[R](action: BasicAction[R, NoStream, Effect]) {
      def execute(implicit s: JdbcBackend#Session): R = {
        action
          .asInstanceOf[SynchronousDatabaseAction[R, NoStream, BlockingJdbcActionContext, ?, Effect]]
          .run(new BlockingJdbcActionContext(s))
      }
    }

    /**
     * Extends plain db queries
     */
    implicit class RichDBIOAction[R](action: DBIOAction[R, NoStream, Effect]) {

      def executeAction[T](
        action: DBIOAction[T, NoStream, Effect],
        ctx: backend.JdbcActionContext,
        streaming: Boolean,
        topLevel: Boolean
      ): T = action match {
        case a: SynchronousDatabaseAction[_, _, backend.JdbcActionContext, _, Effect] => a.run(ctx).asInstanceOf[T]
        case FlatMapAction(base, f, ec) =>
          val result = executeAction(base, ctx, false, topLevel)
          executeAction(f(result), ctx, streaming, false)
        case AndThenAction(actions) =>
          val last = actions.length - 1
          val results = actions.zipWithIndex.map { case (action, pos) =>
            executeAction(action, ctx, streaming && pos == last, pos == 0)
          }
          results.last.asInstanceOf[T]
      }

      def run(implicit s: JdbcBackend#Session): R = executeAction(action, new BlockingJdbcActionContext(s), false, true)
    }
  }
}
