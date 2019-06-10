/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.example.datapipeline.util.jdbcsink

import java.sql.{Connection, Driver, DriverManager, JDBCType, PreparedStatement, ResultSet, ResultSetMetaData, SQLException}
import java.util.Locale

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.analysis.Resolver
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.catalyst.expressions.SpecificInternalRow
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser
import org.apache.spark.sql.catalyst.util.{CaseInsensitiveMap, DateTimeUtils, GenericArrayData}
import org.apache.spark.sql.execution.datasources.jdbc.{DriverRegistry, DriverWrapper, JDBCOptions, JdbcOptionsInWrite}
import org.apache.spark.sql.jdbc.{JdbcDialect, JdbcDialects, JdbcType}
import org.apache.spark.sql.types.DecimalType.{MAX_PRECISION, MAX_SCALE}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.unsafe.types.UTF8String

import scala.util.Try
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import scala.math.min

/**
  * Util functions for JDBC tables.
  */
object JdbcUtils extends Logging {
  /**
    * Returns a factory for creating connections to the given JDBC URL.
    * @param options - JDBC options that contains url, table and other information.
    */
  def createConnectionFactory(options: JDBCOptions): () => Connection = {
    val driverClass: String = options.driverClass
    () => {
      DriverRegistry.register(driverClass)
      val driver: Driver = DriverManager.getDrivers.asScala.collectFirst {
        case d: DriverWrapper if d.wrapped.getClass.getCanonicalName == driverClass => d
        case d if d.getClass.getCanonicalName == driverClass => d
      }.getOrElse {
        throw new IllegalStateException(
          s"Did not find registered driver with class $driverClass")
      }
      driver.connect(options.url, options.asConnectionProperties)
    }
  }

  /**
    * Returns true if the table already exists in the JDBC database.
    */
  def tableExists(conn: Connection, options: JdbcOptionsInWrite): Boolean = {
    val dialect = JdbcDialects.get(options.url)

    // Somewhat hacky, but there isn't a good way to identify whether a table exists for all
    // SQL database systems using JDBC meta data call
    Try {
      val statement = conn.prepareStatement(dialect.getTableExistsQuery(options.table))
      try {
        statement.executeQuery()
      } finally {
        statement.close()
      }
    }.isSuccess
  }

  /**
    * Drops a table from the JDBC database.
    */
  def dropTable(conn: Connection, table: String): Unit = {
    val statement = conn.createStatement
    try {
      statement.executeUpdate(s"DROP TABLE $table")
    } finally {
      statement.close()
    }
  }

  /**
    * Truncates a table from the JDBC database without side effects.
    */
  def truncateTable(conn: Connection, options: JdbcOptionsInWrite): Unit = {
    val dialect = JdbcDialects.get(options.url)
    val statement = conn.createStatement
    try {
      statement.executeUpdate(dialect.getTruncateQuery(options.table))
    } finally {
      statement.close()
    }
  }

  def isCascadingTruncateTable(url: String): Option[Boolean] = {
    JdbcDialects.get(url).isCascadingTruncateTable()
  }



  /**
    * Retrieve standard jdbc types.
    *
    * @param dt The datatype (e.g. [[org.apache.spark.sql.types.StringType]])
    * @return The default JdbcType for this DataType
    */
  def getCommonJDBCType(dt: DataType): Option[JdbcType] = {
    dt match {
      case IntegerType => Option(JdbcType("INTEGER", java.sql.Types.INTEGER))
      case LongType => Option(JdbcType("BIGINT", java.sql.Types.BIGINT))
      case DoubleType => Option(JdbcType("DOUBLE PRECISION", java.sql.Types.DOUBLE))
      case FloatType => Option(JdbcType("REAL", java.sql.Types.FLOAT))
      case ShortType => Option(JdbcType("INTEGER", java.sql.Types.SMALLINT))
      case ByteType => Option(JdbcType("BYTE", java.sql.Types.TINYINT))
      case BooleanType => Option(JdbcType("BIT(1)", java.sql.Types.BIT))
      case StringType => Option(JdbcType("TEXT", java.sql.Types.VARCHAR))
      case BinaryType => Option(JdbcType("BLOB", java.sql.Types.BLOB))
      case TimestampType => Option(JdbcType("TIMESTAMP", java.sql.Types.TIMESTAMP))
      case DateType => Option(JdbcType("DATE", java.sql.Types.DATE))
      case t: DecimalType => Option(
        JdbcType(s"DECIMAL(${t.precision},${t.scale})", java.sql.Types.DECIMAL))
      case _ => None
    }
  }

  def getJdbcType(dt: DataType, dialect: JdbcDialect): JdbcType = {
    dialect.getJDBCType(dt).orElse(getCommonJDBCType(dt)).getOrElse(
      throw new IllegalArgumentException(s"Can't get JDBC type for ${dt.simpleString}"))
  }




  /**
    * Maps a JDBC type to a Catalyst type.  This function is called only when
    * the JdbcDialect class corresponding to your database driver returns null.
    *
    * @param sqlType - A field of java.sql.Types
    * @return The Catalyst type corresponding to sqlType.
    */
  private def getCatalystType(
                               sqlType: Int,
                               precision: Int,
                               scale: Int,
                               signed: Boolean): DataType = {
    val answer = sqlType match {
      // scalastyle:off
      case java.sql.Types.ARRAY         => null
      case java.sql.Types.BIGINT        => if (signed) { LongType } else { DecimalType(20,0) }
      case java.sql.Types.BINARY        => BinaryType
      case java.sql.Types.BIT           => BooleanType // @see JdbcDialect for quirks
      case java.sql.Types.BLOB          => BinaryType
      case java.sql.Types.BOOLEAN       => BooleanType
      case java.sql.Types.CHAR          => StringType
      case java.sql.Types.CLOB          => StringType
      case java.sql.Types.DATALINK      => null
      case java.sql.Types.DATE          => DateType
      case java.sql.Types.DECIMAL
        if precision != 0 || scale != 0 => DecimalType(min(precision, MAX_PRECISION), min(scale, MAX_SCALE))
      case java.sql.Types.DECIMAL       => org.apache.spark.sql.types.DecimalType.SYSTEM_DEFAULT
      case java.sql.Types.DISTINCT      => null
      case java.sql.Types.DOUBLE        => DoubleType
      case java.sql.Types.FLOAT         => FloatType
      case java.sql.Types.INTEGER       => if (signed) { IntegerType } else { LongType }
      case java.sql.Types.JAVA_OBJECT   => null
      case java.sql.Types.LONGNVARCHAR  => StringType
      case java.sql.Types.LONGVARBINARY => BinaryType
      case java.sql.Types.LONGVARCHAR   => StringType
      case java.sql.Types.NCHAR         => StringType
      case java.sql.Types.NCLOB         => StringType
      case java.sql.Types.NULL          => null
      case java.sql.Types.NUMERIC
        if precision != 0 || scale != 0 => DecimalType(min(precision, MAX_PRECISION), min(scale, MAX_SCALE))
      case java.sql.Types.NUMERIC       => org.apache.spark.sql.types.DecimalType.SYSTEM_DEFAULT
      case java.sql.Types.NVARCHAR      => StringType
      case java.sql.Types.OTHER         => null
      case java.sql.Types.REAL          => DoubleType
      case java.sql.Types.REF           => StringType
      case java.sql.Types.REF_CURSOR    => null
      case java.sql.Types.ROWID         => LongType
      case java.sql.Types.SMALLINT      => IntegerType
      case java.sql.Types.SQLXML        => StringType
      case java.sql.Types.STRUCT        => StringType
      case java.sql.Types.TIME          => TimestampType
      case java.sql.Types.TIME_WITH_TIMEZONE
      => null
      case java.sql.Types.TIMESTAMP     => TimestampType
      case java.sql.Types.TIMESTAMP_WITH_TIMEZONE
      => null
      case java.sql.Types.TINYINT       => IntegerType
      case java.sql.Types.VARBINARY     => BinaryType
      case java.sql.Types.VARCHAR       => StringType
      case _                            =>
        throw new SQLException("Unrecognized SQL type " + sqlType)
      // scalastyle:on
    }

    if (answer == null) {
      throw new SQLException("Unsupported type " + JDBCType.valueOf(sqlType).getName)
    }

    answer
  }

  /**
    * Returns an Insert SQL statement for inserting a row into the target table via JDBC conn.
    */
  def getInsertStatement(
                          table: String,
                          rddSchema: StructType,
                          tableSchema: Option[StructType],
                          isCaseSensitive: Boolean,
                          dialect: JdbcDialect): String = {
    val columns = if (tableSchema.isEmpty) {
      rddSchema.fields.map(x => dialect.quoteIdentifier(x.name)).mkString(",")
    } else {
      val columnNameEquality = if (isCaseSensitive) {
        org.apache.spark.sql.catalyst.analysis.caseSensitiveResolution
      } else {
        org.apache.spark.sql.catalyst.analysis.caseInsensitiveResolution
      }
      // The generated insert statement needs to follow rddSchema's column sequence and
      // tableSchema's column names.
      val tableColumnNames = tableSchema.get.fieldNames
      rddSchema.fields.map { col =>
        val normalizedName = tableColumnNames.find(f => columnNameEquality(f, col.name)).getOrElse {
          throw new AnalysisException(s"""Column "${col.name}" not found in schema $tableSchema""")
        }
        dialect.quoteIdentifier(normalizedName)
      }.mkString(",")
    }
    val placeholders = rddSchema.fields.map(_ => "?").mkString(",")
    s"INSERT INTO $table ($columns) VALUES ($placeholders)"
  }

  /**
    * Saves the RDD to the database in a single transaction.
    */
  def saveTable(
                 df: DataFrame,
                 tableSchema: Option[StructType],
                 isCaseSensitive: Boolean,
                 options: JdbcOptionsInWrite): Unit = {
    val url = options.url
    val table = options.table
    val dialect = JdbcDialects.get(url)
    val rddSchema = df.schema
    val getConnection: () => Connection = createConnectionFactory(options)
    val batchSize = options.batchSize
    val isolationLevel = options.isolationLevel

    val insertStmt = getInsertStatement(table, rddSchema, tableSchema, isCaseSensitive, dialect)
  }

  /**
    * Compute the schema string for this RDD.
    */
  def schemaString(schema: StructType, sparkSession: SparkSession,
                   url: String,
                   createTableColumnTypes: Option[String] = None): String = {
    val sb = new StringBuilder()
    val dialect = JdbcDialects.get(url)
    val userSpecifiedColTypesMap = createTableColumnTypes
      .map(parseUserSpecifiedCreateTableColumnTypes(schema, sparkSession, _))
      .getOrElse(Map.empty[String, String])
    schema.fields.foreach { field =>
      val name = dialect.quoteIdentifier(field.name)
      val typ = userSpecifiedColTypesMap
        .getOrElse(field.name, getJdbcType(field.dataType, dialect).databaseTypeDefinition)
      val nullable = if (field.nullable) "" else "NOT NULL"
      sb.append(s", $name $typ $nullable")
    }
    if (sb.length < 2) "" else sb.substring(2)
  }

  /**
    * Parses the user specified createTableColumnTypes option value string specified in the same
    * format as create table ddl column types, and returns Map of field name and the data type to
    * use in-place of the default data type.
    */
  private def parseUserSpecifiedCreateTableColumnTypes(
                                                        schema: StructType, sparkSession: SparkSession,
                                                        createTableColumnTypes: String): Map[String, String] = {
    def typeName(f: StructField): String = {
      // char/varchar gets translated to string type. Real data type specified by the user
      // is available in the field metadata as HIVE_TYPE_STRING
      if (f.metadata.contains(HIVE_TYPE_STRING)) {
        f.metadata.getString(HIVE_TYPE_STRING)
      } else {
        f.dataType.catalogString
      }
    }

    val userSchema = CatalystSqlParser.parseTableSchema(createTableColumnTypes)
    val nameEquality = sparkSession.sessionState.conf.resolver

    // checks duplicate columns in the user specified column types.
    SchemaUtils.checkColumnNameDuplication(
      userSchema.map(_.name), "in the createTableColumnTypes option value", nameEquality)

    // checks if user specified column names exist in the DataFrame schema
    userSchema.fieldNames.foreach { col =>
      schema.find(f => nameEquality(f.name, col)).getOrElse {
        throw new AnalysisException(
          s"createTableColumnTypes option column $col not found in schema " +
            schema.catalogString)
      }
    }

    val userSchemaMap = userSchema.fields.map(f => f.name -> typeName(f)).toMap
    val isCaseSensitive = sparkSession.sessionState.conf.caseSensitiveAnalysis
    if (isCaseSensitive) userSchemaMap else CaseInsensitiveMap(userSchemaMap)
  }

  /**
    * Creates a table with a given schema.
    */
  def createTable(
                   conn: Connection,
                   df: DataFrame,
                   options: JdbcOptionsInWrite): Unit = {
    createTable(conn, df.schema, df.sparkSession, options)
  }

  /**
    * Creates a table with a given schema.
    */

  def createTable(
                   conn: Connection,
                   schema: StructType, sparkSession: SparkSession,
                   options: JdbcOptionsInWrite): Unit = {
    val strSchema = schemaString(
      schema, sparkSession, options.url, options.createTableColumnTypes)
    val table = options.table
    val createTableOptions = options.createTableOptions
    // Create the table if the table does not exist.
    // To allow certain options to append when create a new table, which can be
    // table_options or partition_options.
    // E.g., "CREATE TABLE t (name string) ENGINE=InnoDB DEFAULT CHARSET=utf8"
    val sql = s"CREATE TABLE $table ($strSchema) $createTableOptions"
    logInfo(sql)
    val statement = conn.createStatement
    try {
      statement.executeUpdate(sql)
    } finally {
      statement.close()
    }
  }

}
