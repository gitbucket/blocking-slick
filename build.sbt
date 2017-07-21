name := "blocking-slick-32"

organization := "com.github.takezoe"

scalaVersion := "2.12.1"

crossScalaVersions := List("2.11.8", "2.12.1")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick"           % "3.2.1",
  "org.scalatest"      %% "scalatest"       % "3.0.1"   % "test",
  "com.h2database"      % "h2"              % "1.4.192" % "test",
  "ch.qos.logback"      % "logback-classic" % "1.1.8"   % "test"
)

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

scalacOptions := Seq("-deprecation", "-feature")

fork in Test := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/takezoe/blocking-slick</url>
    <licenses>
      <license>
        <name>The Apache Software License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/takezoe/blocking-slick</url>
      <connection>scm:git:https://github.com/takezoe/blocking-slick.git</connection>
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

releaseTagName <<= (name, version) map { case (n, v) =>
  //tagName will be like "SLICK-32-0.0.X"
  s"${n.stripPrefix("blocking-").toUpperCase}-$v"
}

scalacOptions in (Compile, doc) ++= Seq(
  "-sourcepath",
  (baseDirectory in LocalRootProject).value.getAbsolutePath,
  "-doc-source-url",
  "https://github.com/takezoe/blocking-slick/tree/" + releaseTagName.value + "€{FILE_PATH}.scala"
)
