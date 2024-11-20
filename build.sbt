name := "blocking-slick"

organization := "com.github.takezoe"

scalaVersion := "3.3.4"

crossScalaVersions := List("2.12.20", "2.13.15", "3.3.4")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.5.2",
  "com.dimafeng" %% "testcontainers-scala" % "0.41.4" % "test",
  "org.testcontainers" % "mysql" % "1.20.4" % "test",
  "com.mysql" % "mysql-connector-j" % "9.1.0" % "test",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % "test",
  "com.h2database" % "h2" % "1.4.192" % "test",
  "ch.qos.logback" % "logback-classic" % "1.5.12" % "test"
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

scalacOptions := Seq("-deprecation", "-feature")

scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq("-Xsource:3")
    case "2.13" =>
      Seq("-Xsource:3-cross")
    case _ =>
      Nil
  }
}

Test / fork := true

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/gitbucket/blocking-slick</url>
    <licenses>
      <license>
        <name>The Apache Software License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/gitbucket/blocking-slick</url>
      <connection>scm:git:https://github.com/gitbucket/blocking-slick.git</connection>
    </scm>
    <developers>
      <developer>
        <id>takezoe</id>
        <name>Naoki Takezoe</name>
      </developer>
    </developers>
)

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseTagName := {
  // tagName will be like "SLICK-32-0.0.X"
  s"${name.value.stripPrefix("blocking-").toUpperCase(java.util.Locale.ROOT)}-${version.value}"
}

Compile / doc / scalacOptions ++= Seq(
  "-sourcepath",
  (LocalRootProject / baseDirectory).value.getAbsolutePath,
  "-doc-source-url",
  "https://github.com/gitbucket/blocking-slick/tree/" + releaseTagName.value + "€{FILE_PATH}.scala"
)
