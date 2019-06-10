package com.example.datapipeline.test.database

import java.sql.{Connection, DriverManager}
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.Breaks.{break, breakable}


object EmbeddedDB {
  def start(implicit envConfig: Config): Connection = {
    Class.forName(envConfig.getString("db.driver"))
    DriverManager.getConnection(envConfig.getString("db.jdbcUrl") + ";create=true")
  }

  def stop(implicit envConfig: Config): Unit = {
    try{
      DriverManager.getConnection(envConfig.getString("db.jdbcUrl") + ";drop=true")
    }
    catch {
      case _: Any => {}
    }
  }
// records read by filereader will be temporarily persists to derby
  def consumeRecordsFrom(table: String, columnNames: List[String], timeout: FiniteDuration = 5000 milliseconds)
                        (implicit config: Config): List[String] = {
    val url = config.getString("db.jdbcUrl")
    val connection = DriverManager.getConnection(url)
    var result = List.empty[String]
    val sleepDurationMillis = 5000
    val sleepCount = Math.max(timeout.toMillis / sleepDurationMillis, 1).toInt
    breakable {
      for (ix <- 0 to sleepCount) {
        if (tableExists(connection, table)) {
          val ps = connection.prepareStatement(s"select * from ${table}")
          val rs = ps.executeQuery()
          result = Iterator.from(0).takeWhile(_ => rs.next()).map(_ =>
            columnNames.foldLeft(new StringBuilder) { (sb, s) => sb append " | " append rs.getString(s) }.toString()).toList
        }
        if (result.size == 0 && ix < sleepCount) {
          Thread.sleep(3sleepDurationMillis)
        }
        else {
          break
        }
      }
    }
    result
  }

  import java.sql.SQLException

  @throws[SQLException]
  private def getDBTables(targetDBConn: Connection): mutable.Set[String] = {
    val dbmeta = targetDBConn.getMetaData
    var tables: mutable.Set[String] = mutable.HashSet[String]()
    val rs = dbmeta.getTables(null, null, null, Array[String]("TABLE"))
    while (rs.next ) tables += rs.getString("TABLE_NAME").toLowerCase
    tables
  }

  private def tableExists (targetDBConn: Connection, tableName: String): Boolean = {
    getDBTables(targetDBConn).contains(tableName.toLowerCase())
  }

}

}
