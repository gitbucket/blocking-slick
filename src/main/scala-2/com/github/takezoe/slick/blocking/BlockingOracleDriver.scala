package com.github.takezoe.slick.blocking

import slick.jdbc.OracleProfile

object BlockingOracleDriver extends OracleProfile with BlockingJdbcProfile
