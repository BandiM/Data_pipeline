import sbt.ExclusionRule
name := "Datapipeline"
version := "1.0"
scalaVersion := "2.13.1"

updateOptions := updateOptions.value.withCachedResolution(true)

//lib resolvers
resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Restlet Repositories" at "http://maven.restlet.org",
  "Hortonworks Repositories" at "http://repo.hortonworks.com/content/repositories/releases/"
  //,"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)
resolvers += Resolver.sonatypeRepo("snapshots")

unmanagedBase := baseDirectory.value / "libs"
unmanagedJars in Compile += file("libs/db-client-2.7.0.1815.jar")


libraryDependencies ++= Seq(

  // Spark
  "org.apache.spark" %% "spark-core" % "2.4.0",
  "org.apache.spark" %% "spark-sql" % "2.4.0",
  "org.apache.spark" %% "spark-streaming" % "2.4.0",

  // Kafka dependencies
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.4.0" excludeAll(ExclusionRule("net.jpountz.lz4", "lz4") ),
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.4.0",

  // Config & logging dependecies
   // "com.typesafe" % "config" %  "0.13.0" , // "1.3.0",

  // Storage
  "org.apache.hadoop" % "hadoop-azure" % "2.7.0",

  // Postgres
  "org.postgresql" % "postgresql" % "42.2.5",

  // test dependencies
  "org.apache.kafka" %% "kafka" % "2.0.0.3.3.1.2-3"% "test" excludeAll(ExclusionRule("org.slf4j", "slf4j-log4j12"),
    ExclusionRule("net.jpountz.lz4", "lz4") , ExclusionRule(organization = "com.sun.jdmk"),
    ExclusionRule (organization = "com.sun.jmx"),
    ExclusionRule (organization = "javax.jms")),
  "org.scalatest" %% "scalatest" % "3.0.5" % "test" ,
  "org.apache.derby" % "derby" % "10.14.2.0" % "test",

  //log properties
//"log4j" % "log4j" % "1.2.15" excludeAll( ExclusionRule(organization = "com.sun.jdmk"), ExclusionRule(organization = "com.sun.jmx"), ExclusionRule(organization = "javax.jms"))
)

parallelExecution in ThisBuild := false
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
