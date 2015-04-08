package models

import lib.{Primitives, Text}
import com.gilt.apidoc.generator.v0.models.InvocationForm
import com.gilt.apidoc.spec.v0.models._
import generator.{ScalaDatatype, ScalaPrimitive, GeneratorUtil, ScalaOperation, ScalaParameter, ScalaResource, ScalaService, ScalaUtil, CodeGenerator}

object Play2RouteGenerator extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], String] = {
    new Play2RouteGenerator(form).invoke
  }
}


/**
 * Generates a Play routes file based on the service description
 * from api.json
 */
case class Play2RouteGenerator(form: InvocationForm) {

  private val GlobalPad = 5

  private val service = form.service
  private val scalaService = ScalaService(service)

  def invoke(): Either[Seq[String], String] = {
    scalaService.resources.flatMap { resource =>
      resource.operations.map { op =>
        Play2Route(scalaService, op, resource)
      }
    } match {
      case Nil => {
        Left(
          Seq("Service does not have any resource operations")
        )
      }
      case all => {
        val maxVerbLength = all.map(_.verb.toString.length).toSeq.sorted.last
        val maxUrlLength = all.map(_.url.length).toSeq.sorted.last
        val (paramStart, pathStart) = all.partition(_.url.startsWith("/:"))

        Right(
          ApidocComments(form.service.version, form.userAgent).toRubyString() + "\n\n" +
            (pathStart ++ paramStart).map { r =>
              Seq(
                r.verb,
                " " * (maxVerbLength - r.verb.toString.length + GlobalPad),
                r.url,
                " " * (maxUrlLength - r.url.length + GlobalPad),
                r.method,
                "(",
                r.params.mkString(", "),
                ")"
              ).mkString("")
            }.mkString("\n")
        )
      }
    }
  }
}

private[models] case class Play2Route(
  ssd: ScalaService,
  op: ScalaOperation,
  resource: ScalaResource
) {

  val verb = op.method
  val url = op.path
  val params = parametersWithTypesAndDefaults(
    op.parameters.
      filter { param =>
        param.location match {
          case ParameterLocation.Form => false
          case ParameterLocation.Query => true
          case ParameterLocation.Path => true
          case ParameterLocation.UNDEFINED(_) => false
        }
      }.
      filter { param =>
        param.datatype match {
          case (_: ScalaPrimitive) | ScalaDatatype.List(_) => true
          case ScalaDatatype.Map(_) => false
          case ScalaDatatype.Option(inner) => true
        }
      }
  )

  val method = "%s.%s".format(
    "controllers." + lib.Text.underscoreAndDashToInitCap(resource.plural),
    GeneratorUtil.urlToMethodName(resource.plural, op.method, url)
  )

  private def parametersWithTypesAndDefaults(params: Iterable[ScalaParameter]): Iterable[String] = {
    params.map { param =>
      Seq(
        Some(definition(param)),
        param.default.map( d =>
          param.datatype match {
            case ScalaDatatype.List(_) => {
              sys.error("Cannot set defaults for lists")
            }
            case ScalaDatatype.Map(_) => {
              sys.error("Cannot set defaults for maps")
            }
            case ScalaDatatype.Option(_) => {
              sys.error("Cannot set defaults for options")
            }
            case p: ScalaPrimitive => {
              "?= " + defaultForPrimitive(p, d)
            }
          }
        )
      ).flatten.mkString(" ")
    }
  }

  private def defaultForPrimitive(
    primitive: ScalaPrimitive,
    value: String
  ): String = primitive match {
    case ScalaPrimitive.String | ScalaPrimitive.DateIso8601 | ScalaPrimitive.DateTimeIso8601 | ScalaPrimitive.Uuid => {
      ScalaUtil.wrapInQuotes(value)
    }
    case ScalaPrimitive.Integer | ScalaPrimitive.Double | ScalaPrimitive.Long | ScalaPrimitive.Boolean | ScalaPrimitive.Decimal => {
      value
    }
    case ScalaPrimitive.Object => {
      "play.api.libs.json.parse(%s)".format(ScalaUtil.wrapInQuotes(value))
    }
    case ScalaPrimitive.Enum(_, _) => {
      "%s(%s)".format(primitive.fullName, ScalaUtil.wrapInQuotes(value))
    }
    case ScalaPrimitive.Model(_, _) | ScalaPrimitive.Union(_, _) | ScalaPrimitive.Unit => {
      sys.error(s"Unsupported type[$primitive] for default values")
    }
  }

  private def definition(scalaParam: ScalaParameter): String = {
    val datatypeName = scalaParam.datatype match {
      case ScalaDatatype.Map(_) => {
        sys.error("Cannot have maps as parameters")
      }
      case ScalaDatatype.List(primitive) => {
        s"List[${primitive.fullName}]"
      }
      case ScalaDatatype.Option(ScalaDatatype.List(primitive)) => {
        s"_root_.scala.Option[List[${primitive.fullName}]]"
      }
      case datatype @ (ScalaDatatype.Option(_) | _: ScalaPrimitive) =>
        datatype.name
    }

    s"${scalaParam.originalName}: $datatypeName"
  }
}

