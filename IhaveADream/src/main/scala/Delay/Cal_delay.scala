package Delay

import java.text.{ParseException, SimpleDateFormat}

import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapred.TextInputFormat
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
  * Created by WJ on 2017/11/2.
  */
object Cal_delay {
  /**
    * 计算时间差（秒）
    * @param time1
    * @param time2
    * @return
    */
  def time_diff(time1:String,time2:String): Long ={
    val sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val timediff = sdf.parse(time1).getTime-sdf.parse(time2).getTime
    timediff/1000
  }

  /**
    * 去除引号
    * @param spark
    * @param path
    * @return
    */
  def clean(spark:SparkSession,path:String):RDD[String]={
    val input = spark.sparkContext.hadoopFile[LongWritable,Text,TextInputFormat](path,1).map(p => new String(p._2.getBytes,0,p._2.getLength,"GB2312"))
    val clean = input.map(line => line.replaceAll("\"",""))
    clean
  }

  /**
    * 计算基本信息
    * @param sparkSession
    * @param path
    */
  def Basic_info(sparkSession: SparkSession,path:String="F:\\电子站牌延时\\1010bus.csv"): Unit ={
    val gotData = clean(sparkSession,path).filter(!_.contains("RECDATETIME")).distinct().map(line=>{
      val splits = line.split(",")
      data(splits(0),splits(1),splits(2),splits(3))
    }).map(x=>{
      try{
        val timeDiff = time_diff(x.receiveTime,x.upTime)
        delay1(x.carID,timeDiff)} catch {
        case e:ParseException => delay1("error",1)
      }
    }).filter(!_.carID.contains("error")).cache()
    val GPStotal = gotData.count()
    val carTotal = gotData.map(_.carID).distinct().count
    val onTime = gotData.filter(_.timeDiff==0).count()
    val delayed = gotData.filter(_.timeDiff>0).map(_.timeDiff/60).countByValue().foreach(println)
    val shebei = gotData.filter(_.timeDiff<0).count
    val average_timediff = gotData.filter(_.timeDiff>0).map(_.timeDiff).mean()
    println("总共有"+GPStotal+"条数据"+","+carTotal+"辆车"+","+"准点："+onTime+","+"设备异常数据："+shebei+","+"平均延时:"+average_timediff+"秒")
  }

  /**
    * 延时超过10分钟
    * @param sparkSession
    * @param path
    */
  def More10min(sparkSession: SparkSession,path:String="F:\\电子站牌延时\\1010dongbu.csv"): Unit ={
    val more10minData = clean(sparkSession,path).filter(!_.contains("RECDATETIME")).map(line=>{
      val splits = line.split(",")
      data(splits(0),splits(1),splits(2),splits(3))
    }).map(x=>{
      try{
        val timeDiff = time_diff(x.receiveTime,x.upTime)/60
        delay(x.carID,x.upTime,x.receiveTime,timeDiff)} catch {
        case e:ParseException => delay("error","1","1",1)
      }
    }).filter(!_.carID.contains("error")).filter(_.timeDiff > 10).foreach(println)
  }

  def SameCompare(sparkSession: SparkSession,here:String="G:\\数据\\公交GPS\\*",there:String="F:\\电子站牌延时\\1010xibu.csv"): Unit ={
    val sc = sparkSession.sparkContext
    import sparkSession.implicits._
    val thereData = clean(sparkSession,there).filter(!_.contains("RECDATETIME")).distinct().map(line=>{
      val splits = line.split(",")
      val sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
   try{   val upTime = sdf.format(sdf.parse(splits(2)))
      val receiveTime = sdf.format(sdf.parse(splits(3)))
      data(splits(0),splits(1),upTime,receiveTime)}catch {
     case e:ParseException => data("1","1","1","1")
   }
    }).filter(!_.carID.equals("1")).toDF
    val here1 = sc.hadoopFile[LongWritable,Text,TextInputFormat](here,1).map(p => new String(p._2.getBytes,0,p._2.getLength,"GB2312")).filter(_.split(",").length > 14)
    val hereData = here1.map(line =>{
      val splits = line.split(",")
      val sdfTo = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
      val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
   try{   val upTime = sdfTo.format(sdf.parse("20"+splits(11)))
      val receiveTime = sdfTo.format(sdf.parse(splits(0)))
      data(splits(3),splits(2),upTime,receiveTime)}catch{
     case e:ParseException => data("1","1","1","1")
   }
    }).filter(!_.carID.equals("1")).toDF

    val joined = hereData.join(thereData,Seq("carID","upTime")).map(row =>{
      val carId = row.getString(row.fieldIndex("carID"))
      val upTime = row.getString(row.fieldIndex("upTime"))
      val receiveTime1 = row.getString(3)
      val receiveTime2 = row.getString(5)
      val timeDiff = time_diff(receiveTime1,receiveTime2)
      (carId,upTime,receiveTime1,receiveTime2,timeDiff)
    }).rdd.coalesce(1).saveAsTextFile("G:\\数据\\west_result")
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]").appName("Cal_delay").config("spark.sql.warehouse.dir", "F:/Github/IhaveADream/spark-warehouse").getOrCreate()
    val sc = spark.sparkContext
    SameCompare(spark)


  }

  case class data(carID:String,company:String,upTime:String,receiveTime:String)
  case class delay(carID:String,upTime:String,receiveTime:String,timeDiff:Long)
  case class delay1(carID:String,timeDiff:Long)
}
