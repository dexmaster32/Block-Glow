plugins {
  `java-library`
  id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
  id("xyz.jpenilla.run-paper") version "2.3.1"
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.dexmaster"
version = "1.0.0"
description = "BlockDisplay - Spawn glowing shulker box block displays with configurable colors and automatic cleanup"

java {
  toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
  paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
}

tasks {
  compileJava {
    options.release = 21
  }
  
  javadoc {
    options.encoding = Charsets.UTF_8.name()
  }
  
  shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("BlockDisplay-${project.version}.jar")
  }
  
  build {
    dependsOn(shadowJar)
  }
} 