name := "blocking-slick"

organization := "com.github.takezoe"

scalaVersion := "3.3.3"

crossScalaVersions := List("2.12.19", "2.13.14", "3.3.3")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.5.1",
  "com.dimafeng" %% "testcontainers-scala" % "0.41.4" % "test",
  "org.testcontainers" % "mysql" % "1.20.1" % "test",
  "com.mysql" % "mysql-connector-j" % "9.0.0" % "test",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % "test",
  "com.h2database" % "h2" % "1.4.192" % "test",
  "ch.qos.logback" % "logback-classic" % "1.5.7" % "test"
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

scalacOptions := Seq("-deprecation", "-feature")

scalacOptions ++= {
  if (scalaBinaryVersion.value != "3") {
    Seq("-Xsource:3")
  } else {
    Nil
  }
}

Test / scalacOptions ++= {
  if (scalaBinaryVersion.value == "3") {
    Seq("-source:3.0-migration")
  } else {
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
