package com.example.datapipeline.file

import java.util.logging.LogManager

import com.example.datapipeline.util.ConfigUtil
import org.apache.hadoop.conf.Configuration
import org.apache.log4j.LogManager
import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.sql.functions.{col, hash, struct, to_json}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, SQLContext, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object FileReader {

  // Usage [Config name]
  def main(args: Array[String]) = {
    val log = LogManager.getLogger(this.getClass.getSimpleName)
    //getting available elements list from FileReaderConfig class
    val availableNames = FileReaderConfig.elementsList.map(_.toString).mkString(", ")
    if (args.size == 1) {
      val config = FileReaderConfig.fromString(args(0))
      if (config.isDefined) {
        load(config.get).awaitTermination()
      }
      else {
        log.error("Config name not matched. Should be one of: " + availableNames)
      }
    }
    else {
      log.error("Expecting one arg. Should be one of: " + availableNames)
    }
  }

  def load(mapping: FileReaderConfig): StreamingQuery = {
    load(mapping.topic, mapping.schema, mapping.container, mapping.key, mapping.dataLocation, mapping.filetype, mapping.sec_key)
  }

  // implementation of the load method
  def load(topicName: String, schema: StructType, container: String, key: String, dataLocation: String, filetype: String, sec_key: Option[String]): StreamingQuery = {
    val Configenv = ConfigUtil.loadConfig()
    val master = Configenv.getString("spark.server")
    val checkpointPath = Configenv.getString("spark.checkpoint.location") + "_" + container
    val columnNames = schema.fields.map(x => x.name)

    val conf = new SparkConf().setMaster(master)
      .setAppName(this.getClass.getSimpleName)
      .set("spark.sql.streaming.checkpointLocation", checkpointPath)

    val hadoopConf: Configuration = SparkHadoopUtil.get.conf

    val sparkSession = SparkSession.builder()
      .appName(s"Loader [$topicName -> $container]")
      .config(conf = conf)
      .getOrCreate()

    import sparkSession.implicits._

    val raw_ds = readStream(sparkSession.sqlContext,
      Configenv.getString("testdata.storage_path").replace("CONTAINER", dataLocation).replace("PATH", container + "/*/"), schema, filetype)

    //  Converting data into json format and sorting based on the key column
    //to make sure related items are loading in to same data fame
    val json_ds = raw_ds.withColumn("value", to_json(struct(columnNames.map(a => col(a)): _*)))
      .withColumn("finalkey", when(col(key).isNull, col(sec_key.getOrElse(key))).otherwise(col(key)))
      .withColumn("key", hash(col("finalkey"))
        .select($"key", $"value")

    val query = writeStream(json_ds, Configenv.getString("kafka.bootstrap_servers"), topicName, checkpointPath)
    (query)
  }

  /*
 # Reads data from file(psv or json)
 # FileReaderCongig file we are specifiyng filetype
  */
  def readStream(sqlContext: SQLContext, filename: String, schema: StructType, filetype: String): DataFrame = {
    if (filetype == "psv") {
      sqlContext
        .readStream
        .option("sep", "|")
        .option("header", "true")
        .schema(schema) // Specify schema of the csv files
        .csv(filename)
    }
    else (
      sqlContext
        .readStream
        .option("sep", "|")
        .option("header", "true")
        .schema(schema) // Specify schema of the csv files
        .json(filename)
      )
  }

  /*
  # dataframe is wrtting to Kafka topic
     */
  def writeStream(dataFrame: DataFrame, servers: String, topic: String, checkpointPath: String) = {
    dataFrame
      .selectExpr("value", "key")
      .writeStream
      .format("kafka")
      .trigger(Trigger.ProcessingTime("60 seconds"))
      .option("kafka.bootstrap.servers", servers)
      .option("topic", topic)
      .option("checkpointLocation", checkpointPath)
      .start()
  }

}
