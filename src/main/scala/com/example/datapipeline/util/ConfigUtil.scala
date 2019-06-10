package com.example.datapipeline.util
import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config._

object ConfigUtil {

  private val defaultEnv: AtomicReference[String] = new AtomicReference("test")
  def setDefaultEnvironment(env: String) = defaultEnv.set(env)

  def loadConfig():Config   ={
    val env = if (System.getenv("SCALA_ENV") == null) defaultEnv.get() else System.getenv("SCALA_ENV")
    val conf = ConfigFactory.load()
    conf.getConfig(env)
  }

}
