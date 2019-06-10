package com.example.datapipeline.schema

import java.time.OffsetDateTime
import org.apache.avro.generic.GenericData
import org.apache.spark.sql.types._

object DataSchemas {
//schemas for item and Transaction
  val itemSchema = new StructType() //fields in the file
    .add("CUST_ITEM_ID" ,   StringType)
    .add("EFF_DT",          TimestampType)
    .add("CUST_ID",         IntegerType)
    .add("ITEM_TYPE",    StringType)
    .add("IS_SOLD_FLAG",    StringType)
    .add("BUSINESS_DT",     TimestampType)


  val TransactionJSONschema = new StructType()
    .add("header",     new StructType()
    .add("TRXN_ID",        IntegerType)
    .add("CUST_ID",        IntegerType)
    .add("ORDER_NUM",      StringType)
    .add("BUSINESS_DT",    TimestampType)
    )
    .add("lines", new ArrayType(  new StructType() // array type meaning it can have multipulr lines for singe header
      .add("ITEM_KEY",          IntegerType)
      .add("QTY",      DoubleType)
      .add("QTY_PER_PACKAGE",   DoubleType)
      .add("PRICE",    DoubleType)
      , false))

}

