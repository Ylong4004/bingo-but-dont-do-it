package me.jfenn.bingo.common.stats

import com.zaxxer.hikari.HikariDataSource

class FakeConnectionFactory {

    private val url = "jdbc:sqlite:file:memdb1?mode=memory"

    private val dataSource = HikariDataSource().apply {
        jdbcUrl = url
        username = ""
        password = ""
    }

}