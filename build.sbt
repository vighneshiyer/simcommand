scalaVersion := "2.12.15"

scalacOptions := Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-P:chiselplugin:genBundleElements",
)

addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.2" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "edu.berkeley.cs" %% "chisel3" % "3.5.2",
  "edu.berkeley.cs" %% "chiseltest" % "0.5.2",
  "io.spray" %%  "spray-json" % "1.3.5"
)

fork in run := true
// hprof is deprecated
// javaOptions in run += "-agentlib:hprof=cpu=samples,depth=400,interval=100,lineno=y,thread=y,file=out.hprof"