package com.newzly.cassandra.phantom.dsl


import java.net.InetAddress
import java.util.{ Date, UUID }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import com.datastax.driver.core.{ Session, Row }
import com.datastax.driver.core.utils.UUIDs

import com.newzly.cassandra.phantom._
import com.newzly.cassandra.phantom.CassandraTable

class InsertTable extends BaseTest {


  implicit val session: Session = cassandraSession

  "Insert" should "work fine for primitives columns" in {
    //char is not supported
    //https://github.com/datastax/java-driver/blob/2.0/driver-core/src/main/java/com/datastax/driver/core/DataType.java
    val primitivesTable =
      """|CREATE TABLE Primitives(
        |str text PRIMARY KEY,
        |long bigint,
        |boolean boolean,
        |bDecimal decimal,
        |double double,
        |float float,
        |inet inet,
        |int int,
        |date timestamp,
        |uuid uuid,
        |bi varint);
      """.stripMargin
    cassandraSession.execute(primitivesTable)

    case class Primitive(
      str: String,
      long: Long,
      boolean: Boolean,
      bDecimal: BigDecimal,
      double: Double,
      float: Float,
      inet: java.net.InetAddress,
      int: Int,
      date: java.util.Date,
      uuid: java.util.UUID,
      bi: BigInt)

    class Primitives extends CassandraTable[Primitives, Primitive] {
      override def fromRow(r: Row): Primitive = {
        Primitive(str(r), long(r), boolean(r), bDecimal(r), double(r), float(r), inet(r),
          int(r), date(r), uuid(r), bi(r))
      }

      object str extends PrimitiveColumn[String]
      object long extends PrimitiveColumn[Long]

      object boolean extends PrimitiveColumn[Boolean]
      object bDecimal extends PrimitiveColumn[BigDecimal]
      object double extends PrimitiveColumn[Double]
      object float extends PrimitiveColumn[Float]
      object inet extends PrimitiveColumn[InetAddress]
      object int extends PrimitiveColumn[Int]
      object date extends PrimitiveColumn[Date]
      object uuid extends PrimitiveColumn[UUID]
      object bi extends PrimitiveColumn[BigInt]
    }
    object Primitives extends Primitives

    val row = Primitive("myString", 2.toLong, true, BigDecimal("1.1"), 3.toDouble, 4.toFloat,
      InetAddress.getByName("127.0.0.1"), 9, new java.util.Date, com.datastax.driver.core.utils.UUIDs.timeBased(),
      BigInt(1002
      ))
    val rcp = Primitives.insert
      .value(_.str, row.str)
      .value(_.long, row.long)
      .value(_.boolean, row.boolean)
      .value(_.bDecimal, row.bDecimal)
      .value(_.double, row.double)
      .value(_.float, row.float)
      .value(_.inet, row.inet)
      .value(_.int, row.int)
      .value(_.date, row.date)
      .value(_.uuid, row.uuid)
      .value(_.bi, row.bi)
    rcp.execute().sync()
    val recipeF: Future[Option[Primitive]] = Primitives.select.one
    assert(recipeF.sync().get === row)

    assert(Primitives.select.fetch.sync() contains (row))
  }

  it should "work fine with List, Set, Map" in {
    val createTestTable =
      """|CREATE TABLE TestTable(
        |key text PRIMARY KEY,
        |list list<text>,
        |setText set<text>,
        |mapTextToText map<text,text>,
        |setInt set<int>,
        |mapIntToText map<int,text> );
      """.stripMargin

    cassandraSession.execute(createTestTable)

    case class TestRow(key: String,
      list: Seq[String],
      setText: Set[String],
      mapTextToText: Map[String, String],
      setInt: Set[Int],
      mapIntToText: Map[Int, String])

    class TestTable extends CassandraTable[TestTable, TestRow] {

      object key extends PrimitiveColumn[String]
      object list extends SeqColumn[String]
      object setText extends SetColumn[String]
      object mapTextToText extends MapColumn[String, String]
      object setInt extends  SetColumn[Int]
      object mapIntToText extends MapColumn[Int, String]

      def fromRow(r: Row): TestRow = {
        TestRow(key(r), list(r),
          setText(r),
          mapTextToText(r),
          setInt(r).toSet,
          mapIntToText(r))
      }
    }
    val row = TestRow("w", Seq("ee", "pp", "ee3"), Set("u", "e"), Map("k" -> "val"), Set(1, 22, 2),
      Map(3 -> "OO"))
    object TestTable extends TestTable

    val rcp = TestTable.insert
      .value(_.key, row.key)
      .value(_.list, row.list)
      .value(_.setText, row.setText)
      .value(_.mapTextToText, row.mapTextToText)
      .value(_.setInt, row.setInt)
      .value(_.mapIntToText, row.mapIntToText)

    rcp.execute().sync()
    val recipeF: Future[Option[TestRow]] = TestTable.select.one
    assert(recipeF.sync().get === row)

    assert(TestTable.select.fetch.sync() contains (row))
  }

  it should "work fine with custom types" in {
    val createTestTable =
      """|CREATE TABLE MyTest(
        |key text PRIMARY KEY,
        |optionA int,
        |classS text,
        );
      """.stripMargin //
    session.execute(createTestTable)

    case class ClassS(something: String)
    case class TestRow(key: String, optionA: Option[Int], classS: ClassS)

    class MyTest extends CassandraTable[MyTest, TestRow] {
      def fromRow(r: Row): TestRow = {
        TestRow(key(r), optionA(r), classS(r))
      }
      val key = column[String]
      val optionA = optColumn[Int]
      val classS = jsonColumn[ClassS]
    }

    val row = TestRow("someKey", Some(2), ClassS("lol"))
    object MyTest extends MyTest

    val rcp = MyTest.insert
      .value(_.key, row.key)
      .valueOrNull(_.optionA, row.optionA)
      .value(_.classS, row.classS)
    rcp.execute().sync()
    val recipeF: Future[Option[TestRow]] = MyTest.select.one
    assert(recipeF.sync().get === row)

    assert(MyTest.select.fetch.sync() contains (row))
  }

  it should "work fine with Mix" in {
    val recipesTable =
      """|CREATE TABLE Recipes (
        |url text PRIMARY KEY,
        |description text,
        |ingredients list<text>,
        |author text,
        |servings int,
        |tags set<text>,
        |last_checked_at timestamp,
        |props map<text, text>,
        |uid timeuuid);
      """.stripMargin
    session.execute(recipesTable)

    case class Author(firstName: String, lastName: String, bio: Option[String])

    case class Recipe(
      url: String,
      description: Option[String],
      ingredients: Seq[String],
      author: Option[Author],
      servings: Option[Int],
      lastCheckedAt: java.util.Date,
      props: Map[String, String])

    class Recipes extends CassandraTable[Recipes, Recipe] {

      override def fromRow(r: Row): Recipe = {
        Recipe(url(r), description(r), ingredients(r), author.optional(r), servings(r), lastCheckedAt(r), props(r))
      }

      object url extends PrimitiveColumn[String]
      object description extends OptionalPrimitiveColumn[String]
      object ingredients extends SeqColumn[String]
      object author extends JsonTypeColumn[Author]
      object servings extends OptionalPrimitiveColumn[Int]
      object lastCheckedAt extends PrimitiveColumn[Date]
      object props extends MapColumn[String, String]
      object uid extends PrimitiveColumn[UUID]

    }
    implicit val formats = net.liftweb.json.DefaultFormats

    val author = Author("Tony", "Clark", Some("great chef..."))
    val r = Recipe("recipe_url", Some("desc"), Seq("ingr1", "ingr2"), Some(author), Some(4), new java.util.Date, Map("a" -> "b", "c" -> "d"))

    object Recipes extends Recipes
    val rcp = Recipes.insert
      .value(_.url, r.url)
      .valueOrNull(_.description, r.description)
      .value(_.ingredients, r.ingredients)
      .valueOrNull(_.author, r.author)
      .valueOrNull(_.servings, r.servings)
      .value(_.lastCheckedAt, r.lastCheckedAt)
      .value(_.props, r.props)
      .value(_.uid, UUIDs.timeBased())

    rcp.execute().sync()

    val recipeF: Future[Option[Recipe]] = Recipes.select.one
    recipeF.sync()

  }

  ignore should "work here but it fails- WE NEED TO FIX IT" in {
    val createTestTable =
      """|CREATE TABLE TestTable2(
        |key text PRIMARY KEY,
        |optionA int,
        |classS text,
        |optionS text
        |mapIntoClass map<text,text>
        );
      """.stripMargin //        #|
    session.execute(createTestTable)

    case class ClassS(something: Map[String, Int])
    case class TestRow(key: String, optionA: Option[Int], classS: ClassS, optionS: Option[ClassS], map: Map[String, ClassS])

    class TestTable2 extends CassandraTable[TestTable2, TestRow] {
      def fromRow(r: Row): TestRow = {
        TestRow(key(r), optionA(r), classS(r), optionS(r), mapIntoClass(r))
      }
      object key extends PrimitiveColumn[String]
      object optionA extends OptionalPrimitiveColumn[Int]
      object classS extends JsonTypeColumn[ClassS]
      object optionS extends JsonTypeColumn[Option[ClassS]]
      object mapIntoClass extends JsonTypeColumn[Map[String, ClassS]]
    }

    val row = TestRow("someKey", Some(2), ClassS(Map("k2" -> 5)), Some(ClassS(Map("k2" -> 5))), Map("5" -> ClassS(Map("p" -> 2))))
    object TestTable2 extends TestTable2

    val rcp = TestTable2.insert
      .value(_.key, row.key)
      .valueOrNull(_.optionA, row.optionA)
      .value(_.classS, row.classS)
      .value(_.optionS, row.optionS)
      .value(_.mapIntoClass, row.map)


    rcp.qb.enableTracing()

    info(rcp.toString)
    info(rcp.qb.toString)

    rcp.execute().sync()

    val recipeF: Future[Option[TestRow]] = TestTable2.select.one

    assert(recipeF.sync().get === row)
    assert(TestTable2.select.fetch.sync() contains (row))
  }

}
