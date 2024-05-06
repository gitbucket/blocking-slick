package com.github.takezoe.slick.blocking

import slick.jdbc._

object BlockingDerbyDriver extends DerbyProfile with BlockingJdbcProfile
object BlockingH2Driver extends H2Profile with BlockingJdbcProfile
object BlockingHsqldbDriver extends HsqldbProfile with BlockingJdbcProfile
object BlockingMySQLDriver extends MySQLProfile with BlockingJdbcProfile
object BlockingPostgresDriver extends PostgresProfile with BlockingJdbcProfile
object BlockingSQLiteDriver extends SQLiteProfile with BlockingJdbcProfile
object BlockingDB2Driver extends DB2Profile with BlockingJdbcProfile
object BlockingSQLServerDriver extends SQLServerProfile with BlockingJdbcProfile
object BlockingOracleDriver
    extends OracleProfile
    with JdbcActionComponent.OneRowPerStatementOnly
    with BlockingJdbcProfile
