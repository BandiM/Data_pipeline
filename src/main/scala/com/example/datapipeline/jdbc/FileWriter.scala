package com.example.datapipeline.jdbc

import com.example.datapipeline.util.ConfigUtil
import com.typesafe.config.Config
import org.apache.hadoop.conf.Configuration
import org.apache.log4j.LogManager
import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.streaming.{OutputMode, StreamingQuery, Trigger}
import org.apache.spark.sql.types.{DataType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, ForeachWriter, Row, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.sql.functions._

object FileWriter {
  def main(args: Array[String]) = {
    val log = LogManager.getLogger(this.getClass.getSimpleName)
    //getting available elements list from FileReaderConfig class
    val availableNames = FileWriterConfig.elements.map(_.toString).mkString(", ")
    if (args.size == 1) {
      val config = FileWriterConfig.fromString(args(0))
      if (config.isDefined) {
        load(config.get).sparkSession.streams.awaitAnyTermination()
      }
      else {
        log.error("Config name not matched. Should be one of: " + availableNames)
      }
    }
    else {
      log.error("Expecting one arg. Should be one of: " + availableNames)
    }
  }

  def load(mapping: FileWriterConfig): StreamingQuery = {
    load(mapping.topic, mapping.table, mapping.schema, mapping.multipleSink)
  }
  // implementation of the load method
  def load(topicName: String, tableName: String, schema: StructType, multipleSink: List[MultipleSink] = List.empty): StreamingQuery = {

    val ConfigEnv = ConfigUtil.loadConfig()
    val log = LogManager.getLogger(this.getClass.getSimpleName)

    val master = ConfigEnv.getString("spark.server")
    val checkpointPath = ConfigEnv.getString("spark.checkpoint.location") + "_" + tableName

    val conf = new SparkConf().setMaster(master)
                   .setAppName(this.getClass.getSimpleName)
                   .set("spark.sql.streaming.checkpointLocation", checkpointPath)

    val hadoopConf: Configuration = SparkHadoopUtil.get.conf

    val sparkSession = SparkSession.builder()
            .appName(s"Loader [$topicName -> $tableName]")
            .config(conf = conf)
            .getOrCreate()
   // Reading json format data from kakfa topic
    import sparkSession.implicits._
    val raw_ds = readStream(sparkSession, ConfigEnv, topicName)
    // Converting back to original schema defined in DataSchemas class
    val dsFinal = raw_ds.selectExpr("CAST(value AS STRING)")
      .withColumn("data", from_json($"value", schema))
      .select("data.*")

    // Below codes wrties to multipule sinks at a time based on the filter cretier metioned in the FileWriterConfig
    if (multipleSink.nonEmpty) {
      val query2 = multipleSink.map {
        m =>
          var dsFinal2 = dsFinal.select("*")
          if (m.filter.nonEmpty) {
            dsFinal2 = dsFinal2.filter(m.filter.get)
          }
          // Reading array type elemets in teh data
          if (m.explode.nonEmpty) {
            m.explode.foreach {
              f =>
                dsFinal2 = dsFinal2.select(explode_outer($"${f.columnName}").as(f.newColumnName), struct("*").as("exploded_data"))
                  .drop(s"exploded_data.${f.columnName}")
                  .select(f.newColumnName, "exploded_data.*")
            }
          }
          if (m.select.nonEmpty) {
            dsFinal2 = dsFinal2.select(m.select: _*)

          }

          if (m.dropcol.nonEmpty) {
            dsFinal2 = dsFinal2.drop(m.dropcol: _*)
          }
          //finally after all transformation and or filtering data converting data to defined schmea
          if (m.to_json.nonEmpty) {
            dsFinal2 = dsFinal.toJSON.withColumnRenamed("data", m.to_json.get)
            writeStreamForeach(ConfigEnv, dsFinal2, m.tableName)
          } else {
            writeStream(ConfigEnv, dsFinal2, m.tableName, m.checkpointPath)
          }
      }
      query2.head
    }
    else {
      val query = writeStream(ConfigEnv, dsFinal, tableName, checkpointPath)
      (query)
    }

  }
  //reads data from kafka topic
  def readStream(sparkSession: SparkSession, ConfigEnv: Config, topicName: String): DataFrame = {
    sparkSession.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", ConfigEnv.getString("kafka.bootstrap_servers")) // comma separated list of broker:host
      .option("subscribe", topicName) // comma separated list of topics
      .option("startingOffsets", "latest")
      .load()
  }

// writes to postgres DB
  def writeStream(ConfigEnv: Config,
                  dataFrame: DataFrame,
                  tableName: String
                  , checkpointPath: String) = {
    val formatName = ("com.example.datapipeline.util.util.jdbcsink.DefaultSource")

    dataFrame
      .writeStream
      .format(formatName)
      .option("url", ConfigEnv.getString("db.jdbcUrl"))
      .option("driver", ConfigEnv.getString("db.driver"))
      .option("dbtable", tableName)
      .option("batchsize", 1000)
      .option("checkpointLocation", checkpointPath+tableName)
      .outputMode(OutputMode.Append)
      .start()
  }
  //each record store as a json
  def writeStreamForeach(ConfigEnv: Config,
                         dataFrame: DataFrame,
                         tableName: String) = {
    dataFrame.writeStream
      .foreach {
        new ForeachWriter[Row] {
          var connection: java.sql.Connection = _
          var statement: java.sql.Statement = _

          override def open(partitionId: Long, epochId: Long): Boolean = {
            Class.forName(ConfigEnv.getString("db.driver"))
            connection = java.sql.DriverManager.getConnection(ConfigEnv.getString("db.jdbcUrl"))
            statement = connection.createStatement()
            true
          }

          override def process(value: Row): Unit = {
            val sql = s"INSERT INTO ${tableName} (trxn) VALUES ('${value.getAs[String](0)}');"
            statement.executeUpdate(sql)
          }

          override def close(errorOrNull: Throwable): Unit = {
            connection.close()
          }
        }
      }.start()
  }
}
S