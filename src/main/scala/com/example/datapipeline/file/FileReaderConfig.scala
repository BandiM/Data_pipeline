package com.example.datapipeline.file

import com.example.datapipeline.schema.DataSchemas._
import org.apache.spark.sql.types._

//config for Filereader : item is psv file type and TransactionJSON is json file types
case object Item extends FileReaderConfig("item",  "psvfile",itemSchema, "CUST_ID", "psv")
case object TransactionJSON extends FileReaderConfig("transaction", "jsonfile", TransactionJSONschema, "header.TRXN_ID", "json", sec_key = Some("header.CUST_ID"))

sealed abstract class FileReaderConfig(val topic: String,
                                       val container: String,
                                       val schema: StructType,
                                       val key: String,
                                       val partitionSize: Int,
                                       val filetype: String,
                                       val dataLocation: String = "testdata",
                                       val sec_key: Option[String] = None)

object FileReaderConfig {
  val elementsList = Vector(
    Item,
    TransactionJSON)

  def fromString(value: String): Option[FileReaderConfig] = {
    elementsList.find(_.toString == value)
  }
}

