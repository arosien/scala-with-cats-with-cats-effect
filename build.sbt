name := "scala-with-cats-with-cats-effect"
version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8", // source files are in UTF-8
  "-deprecation", // warn about use of deprecated APIs
  "-unchecked", // warn about unchecked type parameters
  "-feature", // warn about misused language features
  "-language:higherKinds", // allow higher kinded types without `import scala.language.higherKinds`
  "-Xlint", // enable handy linter warnings
  "-Xfatal-warnings", // turn compiler warnings into errors
  "-Ypartial-unification" // allow the compiler to unify type constructors of different arities
)

scalacOptions in (Compile, console) ~= (_.filterNot(
  Set(
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )
))

libraryDependencies ++=
  Seq(
    "org.typelevel" %% "cats-core" % "2.0.0",
    "org.typelevel" %% "cats-effect" % "2.0.0",
    "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full
)
