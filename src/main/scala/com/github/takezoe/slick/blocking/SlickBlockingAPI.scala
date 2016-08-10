package com.github.takezoe.slick.blocking

import slick.ast.{CompiledStatement, ResultSetMapping}
import slick.driver.JdbcProfile
import slick.jdbc.{JdbcBackend, JdbcResultConverterDomain}
import slick.lifted.Query
import slick.relational.{CompiledMapping, ResultConverter}
import slick.util.SQLBuilder

import scala.language.existentials
import scala.language.higherKinds
import scala.language.reflectiveCalls

trait SlickBlockingAPI { self: JdbcProfile =>

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

  implicit class BlockingQueryInvoker[U, C[_]](q: Query[_ ,U, C]){

    def list(implicit session: JdbcBackend#Session): Seq[U] = {
      val invoker = createQueryInvoker[U](queryCompiler.run(q.toNode).tree, null, null)
      invoker.results(0).right.get.toSeq
    }

    def first(implicit session: JdbcBackend#Session): U = {
      val invoker = createQueryInvoker[U](queryCompiler.run(q.toNode).tree, null, null)
      invoker.first
    }

    def firstOption(implicit session: JdbcBackend#Session): Option[U] = {
      val invoker = createQueryInvoker[U](queryCompiler.run(q.toNode).tree, null, null)
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

    def unsafeUpdate[T](value: T)(implicit session: JdbcBackend#Session): Int = {
      val tree = updateCompiler.run(q.toNode).tree
      val ResultSetMapping(_, CompiledStatement(_, sres: SQLBuilder.Result, _), CompiledMapping(_converter, _)) = tree
      val converter = _converter.asInstanceOf[ResultConverter[JdbcResultConverterDomain, T]]
      session.withPreparedInsertStatement(sres.sql) { st =>
        st.clearParameters
        converter.set(value, st)
        sres.setter(st, converter.width+1, null)
        st.executeUpdate
      }
    }

    def unsafeInsert[U](value: U)(implicit session: JdbcBackend#Session): Int = {
      val compiled = compileInsert(q.toNode)
      val a = compiled.standardInsert
      session.withPreparedStatement(a.sql) { st =>
        st.clearParameters()
        a.converter.set(value, st)
        st.executeUpdate()
      }
    }
  }

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

}