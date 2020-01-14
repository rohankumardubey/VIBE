package com.leobenkel.vibe.client.pages

import java.time._
import java.time.format.DateTimeFormatter

import com.leobenkel.vibe.client.app.Config
import com.leobenkel.vibe.client.components.AbstractComponent
import com.leobenkel.vibe.core.Messages.{ContentS, MessageWithContentForJson}
import com.leobenkel.vibe.core.Schemas.Traits.{SchemaBase, TableRef}
import com.leobenkel.vibe.core.Utils.SchemaTypes.TABLE_NAME
import io.circe._
import japgolly.scalajs.react.CtorType.ChildArg
import japgolly.scalajs.react.component.Scala.{Component, Unmounted}
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{CtorType, _}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLButtonElement
import typingsJapgolly.semanticDashUiDashReact.components._
import typingsJapgolly.semanticDashUiDashReact.distCommonjsElementsButtonButtonMod.ButtonProps
import ujson.Value.InvalidData

import scala.util.{Failure, Success, Try}

/**
  * A "page" in the application, in this same directory you'd put all of the other application "pages".
  * These are not html pages per se, since we're dealing with a single page com.leobenkel.vibe.client.app. But it's useful to treat
  * each of these as pages internally.
  */
trait ListPageForTable[PK, T <: SchemaBase[PK]] extends AbstractComponent {
  case class State(
    objects: Seq[T] = Seq.empty,
    errors:  Option[String] = None
  )

  protected type ReturnType = MessageWithContentForJson[ContentS[T]]

  protected def getTableRef: TableRef[PK, T]
  lazy final protected val getHeaderColumns: Array[Symbol] = getTableRef.getHeaderColumns
  final protected def getTableValues(obj: T): Array[ChildArg] =
    getTableRef.getTableValues(obj).map {
      case a: Long =>
        Try {
          println(s"cast date")
          //        val df:   SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
          //        val date: String = df.format(a)
          val format = DateTimeFormatter
            .ofPattern("YYYY MM dd HH:mm (Z)", java.util.Locale.getDefault)
          val clock = Clock.fixed(Instant.ofEpochSecond(a), ZoneOffset.ofHours(0))

          println(s"done case date")
          format.format(clock.instant())
        } match {
          case Success(value)     => VdomNode.cast(value)
          case Failure(exception) =>
            exception.printStackTrace()
            VdomNode.cast(s"Failed: ${exception.toString}")
        }
      case a: Boolean => VdomNode.cast(a.toString)
      case a => VdomNode.cast(a)
    }

  lazy private val tableName: TABLE_NAME = getTableRef.getTableName

//  implicit protected def readerT: RW[T]
//  implicit protected def readerC: RW[ContentS[T]]
//  implicit protected def readerL: RW[Array[T]]
//  implicit protected def reader:  RW[ReturnType]

  lazy private val getHostUrl: AsyncCallback[Either[String, String]] = for {
    host <- Config.getKey("host")
    port <- Config.getKey("port")
  } yield {
    for {
      p <- port.flatMap {
        _.asNumber
          .flatMap(_.toInt)
          .fold[Either[String, Int]](Left("Could not covert to 'Int'"))(Right(_))
      }
      h <- host
        .flatMap {
          _.asString
            .fold[Either[String, String]](Left("Could not covert to 'String'"))(Right(_))
        }
    } yield {
      s"$h:$p"
    }
  }

  type DecodingType = T
  protected def decoderT: Decoder[DecodingType]
//  implicit def classTagT: ClassTag[T]

  lazy private val decoderContent: Decoder[ContentS[T]] = (c: HCursor) => {
    println(s"Content: ${c.keys}")
    for {
      length <- c.downField("length").as[Int]
      _ = println(s"Length: $length")
      itemsJ <- c.downField("items").as[Seq[Json]]
      _ = println(s"i: $itemsJ")
      items = itemsJ.map(_.as[T](decoderT).right.get)
    } yield {
      println(s"Items: $items")
      ContentS(items = items, length = length)
    }
  }

  lazy private val decoderMessage: Decoder[ReturnType] = (c: HCursor) => {
    println(s"MessageKeys: ${c.keys}")
    for {
      operation    <- c.downField("operation").as[String]
      success      <- c.downField("success").as[Boolean]
      errorMessage <- c.downField("errorMessage").as[Option[String]]
      items        <- c.downField(tableName).as[ContentS[T]](decoderContent)
    } yield {
      MessageWithContentForJson(
        operation = operation,
        success = success,
        errorMessage = errorMessage,
        content = items
      )
    }
  }

  class Backend($ : BackendScope[_, State]) {
    def init(state:    State): Callback = Callback.empty
    def refresh(state: State): Callback = {
      getHostUrl
        .flatMap {
          case Left(error) =>
            println(s"Error: $error")
            AsyncCallback.apply[CallbackTo[Unit]](_ => $.modState(_.copy(errors = Some(error))))
          case Right(host) =>
            Ajax
              .get(s"http://$host/api/$tableName/all")
              .setRequestContentTypeJsonUtf8
              .send
              .asAsyncCallback
              .map { xhr =>
                try {
                  println(s"Raw: ${xhr.responseText}")
                  val output = io.circe.parser.decode[ReturnType](xhr.responseText)(decoderMessage)
                  println(s"Output: $output")
                  $.modState(_.copy(objects = output.right.get.content.items))
                } catch {
                  case e: InvalidData =>
                    dom.console.error(e.msg + ":" + e.data)
                    throw e
                }
              }
        }.completeWith(_.get)
    }

    def onAddNewObject(
      event: ReactMouseEventFrom[HTMLButtonElement],
      data:  ButtonProps
    ): Callback =
      Callback.alert(
        "Clicked on 'Add New object'... did you expect something else? hey, " +
          "I can't write everything for you!"
      )

    def render(state: State): VdomElement =
      appContext.consume { _ =>
        <.div(
          Table()(
            TableHeader()(
              TableRow()(
                getHeaderColumns.toVdomArray { n =>
                  TableHeaderCell(key = n.name)(VdomNode.cast(n.name))
                }
              )
            ),
            TableBody()(
              state.objects.toVdomArray { obj =>
                TableRow(key = obj.id.toString)(
                  getTableValues(obj).zipWithIndex.toVdomArray {
                    case (r, idx) =>
                      TableCell(key = s"${obj.id.toString}-$idx")(r)
                  }
                )
              }
            )
          ),
          Button(onClick = onAddNewObject)("Add new object")
        )
      }
  }

  lazy private val component: Component[Unit, State, Backend, CtorType.Nullary] = ScalaComponent
    .builder[Unit]("MainPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init($.state) >> $.backend.refresh($.state))
    .build

  final def apply(): Unmounted[Unit, State, Backend] = component()
}
