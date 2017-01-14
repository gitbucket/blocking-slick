package com.github.takezoe.slick.blocking

import java.sql.Connection

import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.JdbcActionContext

class BlockingJdbcActionContext(s: JdbcBackend#Session) extends JdbcActionContext {

  val useSameThread = true
  override def session: slick.jdbc.JdbcBackend.Session = s.asInstanceOf[slick.jdbc.JdbcBackend.Session]
  override def connection: Connection = s.conn

}
