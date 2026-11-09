Vendored dependency: RootShell 1.6 (com.github.Stericson:RootShell:1.6)
Source: https://github.com/Stericson/RootShell (tag 1.6)
License: dual Apache License 2.0 / GPL 2.0 (see the header in each .java file)
Reason vendored: JitPack can no longer build this library (its build.gradle uses
AGP 2.3.2 and fetches from http://repo1.maven.org, which now returns 501), so
the JitPack artifact 404s and the Gradle build cannot resolve it. These 6 files
are the library's complete main sources, copied unchanged; only the Gradle
dependency line was removed from autojs/build.gradle.kts.
