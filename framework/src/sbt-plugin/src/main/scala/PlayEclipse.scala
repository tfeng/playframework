/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play

import sbt._
import sbt.Keys._
import Keys._

trait PlayEclipse {
  this: PlayCommands =>

  private def generateJavaPrefFile(): Unit = {
    val coreSettings = file(".settings") / "org.eclipse.core.resources.prefs"
    if (!coreSettings.exists) {
      IO.createDirectory(coreSettings.getParentFile)
      IO.write(coreSettings,
        """|eclipse.preferences.version=1
           |encoding/<project>=UTF-8""".stripMargin
      )
    }
  }

  /**
   * provides Settings for the eclipse project
   * @param mainLang mainly scala or java?
   */
  def eclipseCommandSettings(mainLang: String): Seq[Setting[_]] = {
    import com.typesafe.sbteclipse.core._
    import com.typesafe.sbteclipse.core.EclipsePlugin._
    import com.typesafe.sbteclipse.core.Validation
    import scala.xml._
    import scala.xml.transform.RewriteRule

    val `/` = java.io.File.separator

    lazy val addClassesManaged = new EclipseTransformerFactory[RewriteRule] {
      override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] = {
        setting(crossTarget in ref, state) map { ct =>
          new RewriteRule {
            override def transform(node: Node): Seq[Node] = node match {
              case elem if (elem.label == "classpath" && (ct / "classes_managed").exists) =>
                val newChild = elem.child ++
                  <classpathentry path={ (ct / "classes_managed").getAbsolutePath } kind="lib"></classpathentry>
                Elem(elem.prefix, "classpath", elem.attributes, elem.scope, false, newChild: _*)
              case other =>
                other
            }
          }
        }
      }
    }

    lazy val addScalaLib = new EclipseTransformerFactory[RewriteRule] {
      override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] = {
        evaluateTask(dependencyClasspath in Runtime, ref, state) map { classpath =>
          val scalaLib =
            classpath.find(_.get(moduleID.key).exists(moduleFilter(organization = "org.scala-lang", name = "scala-library"))).map(_.data.getAbsolutePath)
              .getOrElse(throw new RuntimeException("could not find scala-library.jar"))
          new RewriteRule {
            override def transform(node: Node): Seq[Node] = node match {
              case elem if (elem.label == "classpath") =>
                val newChild = elem.child ++ <classpathentry path={ scalaLib } kind="lib"></classpathentry>
                Elem(elem.prefix, "classpath", elem.attributes, elem.scope, false, newChild: _*)
              case other =>
                other
            }
          }
        }
      }
    }

    lazy val addSourcesManaged = new EclipseTransformerFactory[RewriteRule] {
      override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] = {
        setting(crossTarget in ref, state) map { ct =>
          new RewriteRule {
            override def transform(node: Node): Seq[Node] = node match {
              case elem if (elem.label == "classpath" && (ct / "src_managed" / "main").exists) =>
                val newChild = elem.child ++
                  <classpathentry path={ ct.getName + `/` + "src_managed" + `/` + "main" } kind="src"></classpathentry>
                Elem(elem.prefix, "classpath", elem.attributes, elem.scope, false, newChild: _*)
              case other =>
                other
            }
          }
        }
      }
    }

    mainLang match {
      case SCALA =>
        EclipsePlugin.eclipseSettings ++ Seq(
          EclipseKeys.projectFlavor := EclipseProjectFlavor.Scala,
          EclipseKeys.preTasks := Seq(compile in Compile),
          EclipseKeys.classpathTransformerFactories := Seq(addSourcesManaged)
        )
      case JAVA =>
        generateJavaPrefFile()
        EclipsePlugin.eclipseSettings ++ Seq(
          EclipseKeys.projectFlavor := EclipseProjectFlavor.Java,
          EclipseKeys.preTasks := Seq(compile in Compile),
          EclipseKeys.classpathTransformerFactories := Seq(addClassesManaged, addScalaLib)
        )
    }
  }
}
