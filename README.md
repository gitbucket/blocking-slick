blocking-slick
==============

Provides blocking API for Slick3

Usage
-----

Add following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.github.takezoe" %% "blocking-slick" % "0.0.1"
```

Define an object like follows:
                                      
```scala
package myapp.slick.driver

import slick.driver.H2Driver
import com.github.takezoe.slick.blocking.SlickBlockingAPI

object BlockingH2Driver extends H2Driver with SlickBlockingAPI
```

Then, you can enable blocking API by import this object as follows:

```scala
import myapp.slick.driver.BlockingH2Driver._
import myapp.slick.driver.BlockingH2Driver.api._
```

See the example of use of this blocking API on Play2 at: https://github.com/takezoe/blocking-slick-play2/blob/master/app/controllers/UserController.scala
