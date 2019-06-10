package com.example.datapipeline.test.filetest

import com.example.datapipeline.file._
import com.example.datapipeline.jdbc._
import com.example.datapipeline.test.database.EmbeddedDB
import com.example.datapipeline.test.database.EmbeddedDB._
import com.example.datapipeline.util.ConfigUtil
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class FileReaderTest  extends WordSpecLike with Matchers with BeforeAndAfterAll {

  ConfigUtil.setDefaultEnvironment("test")
  System.setSecurityManager(null)

  implicit val deserializer =  new StringDeserializer()

  override def beforeAll() = {
   //Kafkaserver needs to start() before we ran the job
  }

  override def afterAll() = {
    // Kafka needs to stop() after our job execution done
  }

  s"Loader for Item" must {
    "produce a few messages to a topic within 60 seconds" in {
      .topic)
      val q = FileReader.load(Item)
      val results = consumeMessagesFrom(Item.topic, 60 seconds)
      results.take(5).foreach(x => println(x))
      q.stop()

      assert(results.size > 0)
    }
  }

  s"Loader for JSONTransaction" must {
    "produce a few messages to a topic within 60 seconds" in {

      val q = FileReader.load(TransactionJSON)
      val results = consumeMessagesFrom(TransactionJSON.topic, 60 seconds)
      results.take(5).foreach(x => println(x))
      q.stop()
      assert(results.size > 0)

    }
  }
}
