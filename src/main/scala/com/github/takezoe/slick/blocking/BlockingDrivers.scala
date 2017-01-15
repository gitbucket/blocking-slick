package com.github.takezoe.slick.blocking

import slick.driver._

object BlockingDerbyDriver extends JdbcDriver with DerbyDriver with BlockingJdbcDriver
object BlockingH2Driver extends JdbcDriver with H2Driver with BlockingJdbcDriver
object BlockingHsqldbDriver extends JdbcDriver with HsqldbDriver with BlockingJdbcDriver
object BlockingMySQLDriver extends JdbcDriver with MySQLDriver with BlockingJdbcDriver
object BlockingPostgresDriver extends JdbcDriver with PostgresDriver with BlockingJdbcDriver
object BlockingSQLiteDriver extends JdbcDriver with SQLiteDriver with BlockingJdbcDriver
