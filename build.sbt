name := "blocking-slick-33"

organization := "com.github.takezoe"

scalaVersion := "2.12.8"

crossScalaVersions := List("2.11.12", "2.12.8", "2.13.0")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick"           % "3.3.3",
  "org.scalatest"      %% "scalatest-funsuite" % "3.2.11" % "test",
  "com.h2database"      % "h2"              % "1.4.192" % "test",
  "ch.qos.logback"      % "logback-classic" % "1.2.3"   % "test"
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                  Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

scalacOptions := Seq("-deprecation", "-feature")

fork in Test := true

publishArtifact in Test := false

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
  //tagName will be like "SLICK-32-0.0.X"
  s"${name.value.stripPrefix("blocking-").toUpperCase}-${version.value}"
}

scalacOptions in (Compile, doc) ++= Seq(
  "-sourcepath",
  (baseDirectory in LocalRootProject).value.getAbsolutePath,
  "-doc-source-url",
  "https://github.com/gitbucket/blocking-slick/tree/" + releaseTagName.value + "€{FILE_PATH}.scala"
)
