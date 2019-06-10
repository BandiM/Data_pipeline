package com.example.datapipeline.test.jdbcwritertest

import com.example.datapipeline.file._
import com.example.datapipeline.{jdbc,_}
import com.example.datapipeline.jdbc.FileWriter
import com.example.datapipeline.test.database.EmbeddedDB
import com.example.datapipeline.test.database.EmbeddedDB._
import com.example.datapipeline.util.ConfigUtil
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class FileWritertest  extends WordSpecLike with Matchers with BeforeAndAfterAll  {

  ConfigUtil.setDefaultEnvironment("test")
  System.setSecurityManager(null)

  implicit val deserializer =  new StringDeserializer()

  override def beforeAll() = {
    //Kafkaserver needs to start() before we ran the job
  }

  override def afterAll() = {
    // Kafka needs to stop() after our job execution done
  }

  s"Loader for item.raw topic" must {
    "produce a single message to a topic and observe record in DB within 60 seconds" in {
      //item.raw topic created manually
      // start streaming from topic to DB
      val q1 = FileWriter.load(jdbc.Item)
      val q2 =FileReader.load(file.Item)

      // consume records from table
      val results = consumeRecordsFrom(jdbc.Item.table, List("CUST_ID"), 60 seconds)
      q1.stop()
      q2.stop()

      println(s"Records in the database: ${results.size}" )
      assert(results.size > 0)
    }
  }

  s"Loader for transaction.raw topic" must {
    "produce a single message to a topic and observe record in DB within 60 seconds" in {
      //transaction.raw toipc created
      // start streaming from topic to DB
      val q1 = FileWriter.load(jdbc.TransactionJSON)
      val q2 =FileReader.load(file.TransactionJSON)

      // consume records from table
      val results = consumeRecordsFrom(jdbc.TransactionJSON.table, List("TRXN_ID"), 60 seconds)
      q1.stop()
      q2.stop()

      println(s"Records in the database: ${results.size}" )
      assert(results.size > 0)
    }
  }



}
