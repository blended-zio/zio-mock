import BuildHelper._
import MimaSettings.mimaSettings

inThisBuild(
  List(
    organization  := "dev.zio",
    homepage      := Some(url("https://zio.github.io/zio-mock/")),
    licenses      := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers    := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; mockJVM/compile:scalafix; mockJVM/test:scalafix; mockJVM/scalafmtSbt; mockJVM/scalafmtAll")
addCommandAlias(
  "check",
  "; scalafmtSbtCheck; mockJVM/scalafmtCheckAll; mockJVM/compile:scalafix --check; mockJVM/test:scalafix --check"
)

addCommandAlias(
  "testJVM",
  ";mockTestsJVM/test"
)
addCommandAlias(
  "testJS",
  ";mockTestsJS/test"
)
addCommandAlias(
  "testNative",
  ";mockNative/compile"
)

val zioVersion = "2.0.0-RC1+66-442516e7-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(
    mockJVM,
    mockJS,
    mockNative,
    mockTestsJVM,
    mockTestsJS,
    examplesJVM,
    docs
  )
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true
  )

lazy val mock = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("mock"))
  .settings(
    resolvers +=
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"         % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test"    % zioVersion
    )
  )
  .settings(stdSettings("mock-test"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.1")
        .cross(CrossVersion.for3Use2_13)
    )
  )
  .settings(
    scalacOptions ++= {
      if (scalaVersion.value == Scala3)
        Seq.empty
      else
        Seq("-P:silencer:globalFilters=[zio.stacktracer.TracingImplicits.disableAutoTrace]")
    }
  )

lazy val mockJVM    = mock.jvm
  .settings(dottySettings)
  // No bincompat on zio-test yet
  .settings(mimaSettings(failOnProblem = false))
lazy val mockJS     = mock.js
  .settings(dottySettings)
  .settings(
    libraryDependencies ++= List(
      "io.github.cquiroz" %%% "scala-java-time"      % "2.3.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.3.0"
    )
  )
lazy val mockNative = mock.native
  .settings(nativeSettings)
  .settings(libraryDependencies += "org.ekrich" %%% "sjavatime" % "1.1.5")

lazy val mockTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("mock-tests"))
  .dependsOn(mock)
  .settings(
    resolvers +=
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % zioVersion % Test,
      "dev.zio" %%% "zio-test-sbt" % zioVersion % Test
    )
  )
  .settings(stdSettings("mock-tests"))
  .settings(crossProjectSettings)
  .settings(semanticdbEnabled := false) // NOTE: disabled because it failed on MockableSpec.scala
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(buildInfoSettings("zio.mock"))
  .settings(publish / skip := true)
  .settings(macroExpansionSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val mockTestsJVM = mockTests.jvm.settings(dottySettings)
lazy val mockTestsJS  = mockTests.js.settings(dottySettings)

lazy val examples = crossProject(JVMPlatform)
  .in(file("examples"))
  .dependsOn(mock)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test-junit" % zioVersion
    )
  )
  .settings(stdSettings("mock-tests"))
  .settings(crossProjectSettings)
  .settings(macroExpansionSettings)
  .settings(publish / skip := true)

lazy val examplesJVM = examples.jvm.settings(dottySettings)

lazy val docs = project
  .in(file("zio-mock-docs"))
  .settings(stdSettings("zio-mock"))
  .settings(
    scalaVersion                               := Scala213,
    publish / skip                             := true,
    moduleName                                 := "zio-mock-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(mockJVM),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite                       := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages                   := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(mockJVM)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
