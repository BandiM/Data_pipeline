package com.example.datapipeline.jdbc

import com.example.datapipeline.schema.DataSchemas._
import org.apache.spark.sql.Column
import org.apache.spark.sql.types._

import scala.collection.mutable
import org.apache.spark.sql.functions.{col, _}

case object Item extends FileWriterConfig("item", "item", itemSchema, none)
case object TransactionJSON extends FileWriterConfig("transaction", "None", TransactionJSONschema, multipleSink = List(
                                    MultipleSink("header", "chekpoint_header", Some(col("columnns").equalTo(("value"))
                                      .or(col("columnns").equalTo("value"))
                                      .or(col("columnns").equalTo("APPROVED_ORDER")) ), List.empty, List(col("header.*"))),
                                    MultipleSink("line", "checkpoint_line", Some(col("columnns").equalTo("CONFIRMED_ORDER")
                                      .or(col("columnns").equalTo("PROPOSED_ORDER")) ),
                                      List(ExplodeColumn("lines", "data"),
                                      List(col("header.TRXN_ID"), col("data.*") ))


case class MultipleSink(tableName: String, checkpointPath: String, filter: Option[Column], explode: List[ExplodeColumn] = List.empty[ExplodeColumn], select: List[Column] = List.empty[Column], converttostring: List[String] = List.empty, dropcol: List[String] = List.empty, converttojson: List[String] = List.empty, to_json: Option[String] = None)

case class ExplodeColumn(columnName: String, newColumnName: String)

sealed abstract class FileWriterConfig(val topic: String,
                                       val table: String,
                                       val schema: StructType, val multipleSink: List[MultipleSink] = List.empty)

object FileWriterConfig {
  val elements = Vector(
    Item,
    TransactionJSON)
  def fromString(value: String): Option[FileWriterConfig] = {
    elements.find(_.toString == value)
  }
}
