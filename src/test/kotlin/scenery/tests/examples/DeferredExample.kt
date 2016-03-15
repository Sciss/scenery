package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLException
import org.junit.Test
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTriggerMap
import org.scijava.ui.behaviour.io.InputTriggerConfig
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO
import scenery.*
import scenery.controls.JOGLMouseAndKeyHandler
import scenery.controls.behaviours.FPSCameraControl
import scenery.controls.behaviours.MovementCommand
import scenery.controls.behaviours.ToggleCommand
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.io.*
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class DeferredExample {


    private val scene: Scene = Scene()
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                try {
                    deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4,
                            mClearGLWindow!!.width,
                            mClearGLWindow!!.height)

                    val cam: Camera = DetachedHeadCamera()

                    fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

                    var boxes = (1..20 step 1).map {
                        Box(GLVector(rangeRandomizer(0.5f, 4.0f),
                                rangeRandomizer(0.5f, 4.0f),
                                rangeRandomizer(0.5f, 4.0f)))
                    }

                    boxes.map { i ->
                        i.position =
                                GLVector(rangeRandomizer(-10.0f, 10.0f),
                                        rangeRandomizer(-10.0f, 10.0f),
                                        rangeRandomizer(-10.0f, 10.0f))
                        scene.addChild(i)
                    }

                    var lights = (0..64).map {
                        PointLight()
                    }

                    lights.map {
                        it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f))
                        it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f))
                        it.intensity = rangeRandomizer(0.0001f, 0.001f)

                        scene.addChild(it)
                    }

                    var companionBox = Box(GLVector(5.0f, 5.0f, 5.0f))
                    companionBox.position = GLVector(1.0f, 1.0f, 1.0f)
                    companionBox.name = "Le Box de la Compagnion"
                    val companionBoxMaterial = PhongMaterial()
                    companionBoxMaterial.ambient = GLVector(1.0f, 0.5f, 0.0f)
                    companionBoxMaterial.diffuse = GLVector(1.0f, 0.0f, 0.0f)
                    companionBoxMaterial.specular = GLVector(1.0f, 0.0f, 0.0f)

                    companionBox.material = companionBoxMaterial

                    boxes.first().addChild(companionBox)

                    val sphere = Sphere(0.5f, 20)
                    sphere.position = GLVector(0.5f, -1.2f, 0.5f)
                    sphere.scale = GLVector(5.0f, 5.0f, 5.0f)

                    val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
                    hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
                    val hullboxM = PhongMaterial()
                    hullboxM.ambient = GLVector(0.6f, 0.6f, 0.6f)
                    hullboxM.diffuse = GLVector(0.4f, 0.4f, 0.4f)
                    hullboxM.specular = GLVector(0.0f, 0.0f, 0.0f)
                    hullbox.material = hullboxM

                    val mesh = Mesh()
                    val meshM = PhongMaterial()
                    meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
                    meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
                    meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

                    mesh.readFromOBJ("/Users/ulrik/Code/ClearVolume/scenery/models/sponza.obj")
                    mesh.material = meshM
                    mesh.position = GLVector(155.5f, 150.5f, 55.0f)
                    mesh.scale = GLVector(0.1f, 0.1f, 0.1f)
                    mesh.updateWorld(true, true)
                    mesh.name = "Sponza_Mesh"

                    scene.addChild(mesh)

                    boxes.first().addChild(sphere)

                    cam.position = GLVector(0.0f, 0.0f, 0.0f)
                    cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

                    cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                            70.0f / 180.0f * Math.PI.toFloat(),
                            pDrawable.surfaceWidth.toFloat() / pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f).invert()
                    cam.active = true

                    scene.addChild(cam)

                    var ticks: Int = 0

                    System.out.println(scene.children)

                    thread {
                        var reverse = false
                        val step = 0.02f

                        while (true) {
                            boxes.mapIndexed {
                                i, box ->
                                box.position.set(i % 3, step * ticks)
                                box.needsUpdate = true
                            }

                            lights.mapIndexed {
                                i, light ->
//                                light.position.set(i % 3, step*10 * ticks)
                                val phi = Math.PI * 2.0f * ticks/5000.0f

                                light.position = GLVector(
                                        i*10*Math.sin(phi).toFloat()+Math.exp(i.toDouble()).toFloat(),
                                        step*ticks,
                                        i*10*Math.cos(phi).toFloat()+Math.exp(i.toDouble()).toFloat())

                            }

                            if (ticks >= 5000 && reverse == false) {
                                reverse = true
                            }
                            if (ticks <= 0 && reverse == true) {
                                reverse = false
                            }

                            if (reverse) {
                                ticks--
                            } else {
                                ticks++
                            }

                            Thread.sleep(10)

                            boxes.first().rotation.rotateByEuler(0.01f, 0.0f, 0.0f)
                            boxes.first().needsUpdate = true
                            companionBox.needsUpdate = true
                            sphere.needsUpdate = true
                        }
                    }

                    deferredRenderer?.initializeScene(scene)
                } catch (e: GLException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }

            override fun reshape(pDrawable: GLAutoDrawable,
                                 pX: Int,
                                 pY: Int,
                                 pWidth: Int,
                                 pHeight: Int) {
                var pHeight = pHeight
                super.reshape(pDrawable, pX, pY, pWidth, pHeight)

                if (pHeight == 0)
                    pHeight = 1
                val ratio = 1.0f * pWidth / pHeight
            }

            override fun display(pDrawable: GLAutoDrawable) {
                super.display(pDrawable)

                frameNum++
                deferredRenderer?.render(scene)
                clearGLWindow.windowTitle =  "%.1f fps".format(pDrawable.animator?.lastFPS)
            }

            override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
                mClearGLWindow = pClearGLWindow
            }

            override fun getClearGLWindow(): ClearGLDisplayable {
                return mClearGLWindow!!
            }

        }

        val lClearGLWindow = ClearGLWindow("scenery: DeferredExample",
                1280,
                720,
                lClearGLWindowEventListener)

        lClearGLWindow.isVisible = true
        lClearGLWindow.setFPS(60)

        val inputMap = InputTriggerMap()
        val behaviourMap = BehaviourMap()

        /*
		 * Create a MouseAndKeyHandler that dispatches to registered Behaviours.
		 */
        val handler = JOGLMouseAndKeyHandler()
        handler.setInputMap(inputMap)
        handler.setBehaviourMap(behaviourMap)

        lClearGLWindow.addKeyListener(handler)
        lClearGLWindow.addMouseListener(handler)
        lClearGLWindow.addWindowListener(handler)

        /*
		 * Load YAML config "file".
		 */
        var reader: Reader

        try {
            reader = FileReader(System.getProperty("user.home") + "/.scenery.keybindings")
        } catch (e: FileNotFoundException) {
            System.err.println("Falling back to default keybindings...")
            reader = StringReader("---\n" +
                    "- !mapping" + "\n" +
                    "  action: drag1" + "\n" +
                    "  contexts: [all]" + "\n" +
                    "  triggers: [button1, G]" + "\n" +
                    "- !mapping" + "\n" +
                    "  action: scroll1" + "\n" +
                    "  contexts: [all]" + "\n" +
                    "  triggers: [scroll]" + "\n" +
                    "")
        }

        val config = InputTriggerConfig(YamlConfigIO.read(reader))

        /*
		 * Create behaviours and input mappings.
		 */
        behaviourMap.put("drag1", FPSCameraControl("drag1", scene.findObserver(), lClearGLWindow.width, lClearGLWindow.height))

        behaviourMap.put("move_forward", MovementCommand("move_forward", "forward", scene.findObserver()))
        behaviourMap.put("move_back", MovementCommand("move_back", "back", scene.findObserver()))
        behaviourMap.put("move_left", MovementCommand("move_left", "left", scene.findObserver()))
        behaviourMap.put("move_right", MovementCommand("move_right", "right", scene.findObserver()))
        behaviourMap.put("move_up", MovementCommand("move_up", "up", scene.findObserver()))

        behaviourMap.put("toggle_debug", ToggleCommand("toggle_debug", deferredRenderer!!, "toggleDebug"))
        behaviourMap.put("toggle_ssao", ToggleCommand("toggle_ssao", deferredRenderer!!, "toggleSSAO"))

        val adder = config.inputTriggerAdder(inputMap, "all")
        adder.put("drag1") // put input trigger as defined in config
        adder.put("scroll1", "scroll")
        adder.put("click1", "button1", "B")
        adder.put("click1", "button3", "X")
        adder.put("move_forward", "W")
        adder.put("move_left", "A")
        adder.put("move_back", "S")
        adder.put("move_right", "D")
        adder.put("move_up", "SPACE")

        adder.put("toggle_debug", "X")
        adder.put("toggle_ssao", "O")

        lClearGLWindow.start()

        while (lClearGLWindow.isVisible) {
            Thread.sleep(10)
        }
    }

    @Test fun ScenegraphSimpleDemo() {

    }
}