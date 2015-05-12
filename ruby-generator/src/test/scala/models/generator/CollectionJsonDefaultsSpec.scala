package generator

import com.gilt.apidoc.generator.v0.models.InvocationForm
import models.RubyClientGenerator

import models.TestHelper
import org.scalatest.{FunSpec, Matchers}

class CollectionJsonDefaultsSpec extends FunSpec with Matchers {

  it("generates expected code for ruby client") {
    RubyClientGenerator.invoke(InvocationForm(service = TestHelper.collectionJsonDefaultsService)) match {
      case Left(errors) => fail(errors.mkString(", "))
      case Right(code) => {
        TestHelper.assertEqualsFile("/generators/collection-json-defaults-ruby-client.txt", code)
      }
    }
  }

}


