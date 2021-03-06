package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FontRenderingExample: SceneryDefaultApplication("FontRenderingExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        var lights = (0..5).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(5.0f + i*2.0f, 5.0f, 5.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 1000.0f
            light.quadratic = 0.001f
            light.linear = 0.0f
            scene.addChild(light)
        }

        val hullbox = Box(GLVector(900.0f, 900.0f, 900.0f))
        hullbox.position = GLVector(0.1f, 0.1f, 0.1f)
        val hullboxM = Material()
        hullboxM.ambient = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.specular = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.doubleSided = true
        hullbox.material = hullboxM

        scene.addChild(hullbox)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(5.0f, 0.0f, 15.0f)
            perspectiveCamera(70.0f, windowWidth*1.0f, windowHeight*1.0f, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val board = FontBoard()
        board.text = ""
        board.position = GLVector(0.0f, 0.0f, 0.0f)

        scene.addChild(board)

        thread {
            while(!running) { Thread.sleep(200) }

            arrayOf(
                "hello world!",
                "this is scenery.",
                "demonstrating sdf font rendering."
            ).map {
                Thread.sleep(5000)
                board.text = it
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
