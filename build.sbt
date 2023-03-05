name := "blocking-slick-33"

organization := "com.github.takezoe"

scalaVersion := "2.12.17"

crossScalaVersions := List("2.12.17", "2.13.10", "3.2.2")

Test / sources := {
  if (scalaBinaryVersion.value == "3") {
    Nil // TODO
  } else {
    (Test / sources).value
  }
}

libraryDependencies += {
  if (scalaBinaryVersion.value == "3") {
    "com.typesafe.slick" %% "slick" % "3.5.0-M2" cross CrossVersion.for3Use2_13, // TODO
  } else {
    "com.typesafe.slick" %% "slick" % "3.4.1"
  }
}

libraryDependencies ++= Seq(
  "com.dimafeng" %% "testcontainers-scala" % "0.40.12" % "test",
  "org.testcontainers" % "mysql" % "1.17.6" % "test",
  "mysql" % "mysql-connector-java" % "8.0.32" % "test",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.15" % "test",
  "com.h2database" % "h2" % "1.4.192" % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.11" % "test"
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

scalacOptions := Seq("-deprecation", "-feature")

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
