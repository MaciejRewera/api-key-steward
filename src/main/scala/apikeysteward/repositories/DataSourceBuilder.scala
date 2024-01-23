package apikeysteward.repositories

import apikeysteward.config.DatabaseConfig
import com.zaxxer.hikari.HikariDataSource

object DataSourceBuilder {

  def buildDataSource(dbConfig: DatabaseConfig): HikariDataSource = {
    val ds = new HikariDataSource()
    ds.setDriverClassName(dbConfig.driver)
    ds.setJdbcUrl(dbConfig.uri.toString)
    ds.setUsername(dbConfig.username.getOrElse(""))
    ds.setPassword(dbConfig.password.getOrElse(""))
    ds
  }
}
