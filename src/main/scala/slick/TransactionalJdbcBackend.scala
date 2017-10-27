package slick

import slick.jdbc.JdbcBackend

// JdbcProfileBlockingSession brings back withTransaction feature from slick 2.x
// (it's also related with 3.0).
//
// It cannot use `session.rollback` because we cannot touch `protected var doRollback`.
// Use `session.conn.rollback()` instead.
//
// ref:
// - https://github.com/slick/slick/blob/3.0/slick/src/main/scala/slick/jdbc/JdbcBackend.scala#L424
// - https://github.com/slick/slick/blob/2.1/src/main/scala/scala/slick/jdbc/JdbcBackend.scala#L419
// - https://github.com/slick/slick/blob/3.1/slick/src/main/scala/slick/jdbc/JdbcBackend.scala#L407
trait JdbcProfileBlockingSession {
  /**
   * Extends Session to add methods for session management.
   */
  implicit class BlockingSession(session: JdbcBackend#Session) {
    def withTransaction[T](f: => T): T = {
      val s = session.asInstanceOf[JdbcBackend#BaseSession]
      if(s.isInTransaction) f else {
        s.startInTransaction
        var functionExecuted = false
        try {
          val res = f
          functionExecuted = true
          s.endInTransaction(s.conn.commit())
          res
        } finally {
          if(!functionExecuted) {
            s.endInTransaction(s.conn.rollback())
          }
        }
      }
    }
  }
}
