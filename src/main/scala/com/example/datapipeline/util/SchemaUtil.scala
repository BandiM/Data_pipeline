package com.example.datapipeline.util

import org.apache.spark.sql.types.{DataType, StructType}
import scala.collection.mutable

object SchemaUtil {

  def getOutputSchema(schema: mutable.Map[String, (DataType, DataType)]) = schema.map{case (k, v) => (k, v._2)}

  def toStructType(schema: mutable.Map[String, (DataType, DataType)]): StructType = { var result = new StructType(); schema.foreach{case (k, v) => result = result.add(k, v._1); }; return result }

}
