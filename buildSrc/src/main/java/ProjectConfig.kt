import org.gradle.api.Plugin
import org.gradle.api.Project

open class ProjectConfig {
    val packageName = "eu.darken.capod"
    val applicationId = "app.pa2x2.earside"
    val minSdk = 26

    val compileSdk = 36
    val targetSdk = 36

    override fun toString(): String {
        return "ProjectConfig($packageName, id=$applicationId, min=$minSdk, compile=$compileSdk, target=$targetSdk)"
    }
}

class ProjectConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("projectConfig", ProjectConfig::class.java)
        project.afterEvaluate { println("ProjectConfigPlugin loaded: $extension") }
    }
}
