package graphics.scenery

import cleargl.GLVector
import org.lwjgl.system.MemoryUtil.memAlloc
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.*
import java.util.ArrayList



/**
 * Interface for any [Node] that stores geometry in the form of vertices,
 * normals, texcoords, or indices.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface HasGeometry {
    /** How many elements does a vertex store? */
    val vertexSize: Int
    /** How many elements does a texcoord store? */
    val texcoordSize: Int
    /** The [GeometryType] of the [Node] */
    val geometryType: GeometryType

    /** Array of the vertices */
    var vertices: FloatBuffer
    /** Array of the normals */
    var normals: FloatBuffer
    /** Array of the texcoords */
    var texcoords: FloatBuffer
    /** Array of the indices */
    var indices: IntBuffer

    /**
     * PreDraw function, to be called before the actual rendering, useful for
     * per-timestep preparation.
     */
    fun preDraw() {

    }

    /**
     * Reads an OBJ file's material properties from the corresponding MTL file
     *
     * @param[filename] The filename of the MTL file, stored in the OBJ usually
     * @return A HashMap storing material name and [Material].
     */
    fun readFromMTL(filename: String): HashMap<String, Material> {
        var materials = HashMap<String, Material>()

        var f = File(filename)
        if (!f.exists()) {
            System.out.println("Could not read MTL from $filename, file does not exist.")

            vertices = ByteBuffer.allocateDirect(0).asFloatBuffer()
            normals = ByteBuffer.allocateDirect(0).asFloatBuffer()
            texcoords = ByteBuffer.allocateDirect(0).asFloatBuffer()
            indices = ByteBuffer.allocateDirect(0).asIntBuffer()
            return materials
        }

        val lines = Files.lines(FileSystems.getDefault().getPath(filename))
        var currentMaterial: Material? = Material()

        System.out.println("Reading from MTL file $filename")
        // The MTL file is read line-by-line and tokenized, based on spaces.
        // The first non-whitespace token encountered will be evaluated as command to
        // set up the properties of the [Material], which include e.g. textures and colors.
        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().split(" ").filter { it.length > 0 }
            if (tokens.size > 0) {
                when (tokens[0]) {
                    "#" -> {
                    }
                    "newmtl" -> {
                        currentMaterial = Material()
                        currentMaterial?.name = tokens[1]

                        materials.put(tokens[1], currentMaterial!!)
                    }
                    "Ka" -> currentMaterial?.ambient = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "Kd" -> currentMaterial?.diffuse = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "Ks" -> currentMaterial?.specular = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "d" -> currentMaterial?.opacity = tokens[1].toFloat()
                    "Tr" -> currentMaterial?.opacity = 1.0f - tokens[1].toFloat()
                    "illum" -> {
                    }
                    "map_Ka" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("ambient", mapfile)
                    }
                    "map_Ks" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("specular", mapfile)
                    }
                    "map_Kd" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("diffuse", mapfile)
                    }
                    "map_d" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("displacement", mapfile)
                    }
                    "map_bump" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("normal", mapfile)
                    }
                    "bump" -> {
                    }
                    "Tf" -> {
                    }
                }
            }
        }

        return materials
    }

    /**
     * Read the [Node]'s geometry from an OBJ file, possible including materials
     *
     * @param[filename] The filename to read from.
     * @param[useMTL] Whether a accompanying MTL file shall be used, defaults to true.
     */
    fun readFromOBJ(filename: String, useMTL: Boolean = true) {
        var name: String = ""
        var vbuffer: FloatBuffer = FloatBuffer.allocate(1)
        var nbuffer: FloatBuffer = FloatBuffer.allocate(1)
        var tbuffer: FloatBuffer = FloatBuffer.allocate(1)

        var tmpV = ArrayList<Float>()
        var tmpN = ArrayList<Float>()
        var tmpUV = ArrayList<Float>()

        var boundingBox: FloatArray? = null

        var vertexCount = 0
        var normalCount = 0
        var uvCount = 0

        var materials = HashMap<String, Material>()

        /**
         * Recalculates normals, assuming CCW winding order.
         *
         * @param[vertexBuffer] The vertex list to use
         * @param[normalBuffer] The buffer to store the normals in
         */
        fun calculateNormals(vertexBuffer: FloatBuffer, normalBuffer: FloatBuffer) {
            var i = 0
            while (i < vbuffer.limit() - 1) {
                val v1 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val v2 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val v3 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val a = v2 - v1
                val b = v3 - v1

                val n = a.cross(b).normalized

                normalBuffer.put(n.x())
                normalBuffer.put(n.y())
                normalBuffer.put(n.z())
            }
        }

        fun toBufferIndex(obj: List<Number>, num: Int, vectorSize: Int, offset: Int): Int {
            val index: Int
            if (num >= 0) {
                index = (num - 1) * vectorSize + offset
            } else {
                index = (obj.size / vectorSize + num) * vectorSize + offset
            }

            return index
        }

        fun defaultHandler(x: Int): Float {
            System.err.println("Could not find v/n/uv for index $x. File broken?")
            return 0.0f
        }

        var f = File(filename)
        if (!f.exists()) {
            System.out.println("Could not read from $filename, file does not exist.")

            return
        }

        val start = System.nanoTime()
        var lines = Files.lines(FileSystems.getDefault().getPath(filename))

        var count = 0

        var targetObject = this

        val triangleIndices = intArrayOf(0, 1, 2)
        val quadIndices = intArrayOf(0, 1, 2, 0, 2, 3)
        val ranges = HashMap<Int, IntArray>()
        ranges.put(3, triangleIndices)
        ranges.put(4, quadIndices)

        System.out.println("Reading from OBJ file $filename")
        // OBJ files are read line-by-line, then tokenized after removing trailing
        // and leading whitespace. The first non-whitespace string encountered is
        // evaluated as command, according to the OBJ spec.
        var currentName = name
        var vertexCountMap = HashMap<String, Int>()
        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().split(" ").filter { it.length > 0 }
            if (tokens.size > 0) {
                when (tokens[0]) {
                    "" -> {
                    }
                    "#" -> {
                    }
                    "f" -> {
                        vertexCount += when (tokens.count() - 1) {
                            3 -> 3
                            4 -> 6
                            else -> 0
                        }
                    }
                    "g" -> {
                        vertexCountMap.put(currentName, vertexCount)
                        vertexCount = 0
                        currentName = tokens[1]
                    }
                }
            }
        }

        System.out.println("Found $vertexCount vertices.")

        vertexCountMap.put(currentName, vertexCount)
        vertexCount = 0

        val vertexBuffers = HashMap<String, Triple<FloatBuffer, FloatBuffer, FloatBuffer>>()
        vertexCountMap.forEach { objectName, vertexCount ->
            vertexBuffers.put(objectName, Triple(
                memAlloc(vertexCount * vertexSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                memAlloc(vertexCount * vertexSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                memAlloc(vertexCount * vertexSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            ))
        }

        val br = BufferedReader(FileReader(filename))

        fun String.splitNonEmpty(delim: Char): ArrayList<String> {
            val list = ArrayList<String>()
            var pos = 0
            var end: Int = this.indexOf(delim, pos)

            while (end >= 0) {
                val subs = this.substring(pos, end)
                if(subs.isNotEmpty() && subs != " ") {
                    list.add(subs)
                }
                pos = end + 1

                end = this.indexOf(delim, pos)
            }

            val subs = this.substring(pos)
            if(subs.isNotEmpty() && subs != " ") {
                list.add(subs)
            }

            return list
        }

        fun String.splitCustom(delim: Char): ArrayList<String> {
            val list = ArrayList<String>()
            var pos = 0
            var end: Int = this.indexOf(delim, pos)

            while (end >= 0) {
                val subs = this.substring(pos, end)
                list.add(subs)
                pos = end + 1

                end = this.indexOf(delim, pos)
            }

            val subs = this.substring(pos)
            list.add(subs)

            return list
        }

        br.lines().forEach {
            line ->
            val tokens = line.trim().trimEnd().splitNonEmpty(' ')
            if (tokens.isNotEmpty()) {
                when (tokens[0]) {
                    "" -> {
                    }
                    "#" -> {
                    }
                    "mtllib" -> {
                        if (useMTL) {
                            materials = readFromMTL(filename.substringBeforeLast("/") + "/" + tokens[1])
                        }
                    }
                    "usemtl" -> {
                        if (targetObject is Node && useMTL) {
                            (targetObject as Node).material = materials[tokens[1]]
                        }
                    }
                    "o" -> {
                    }
                // vertices are specified as v x y z
                    "v" -> tmpV.addAll(tokens.drop(1).map(String::toFloat))

                // normal coords are specified as vn x y z
                    "vn" -> tmpN.addAll(tokens.drop(1).map(String::toFloat))

                // UV coords maybe vt t1 t2 0.0 or vt t1 t2
                    "vt" -> {
                        tmpUV.addAll(if (tokens.drop(1).size == 3) {
                            tokens.drop(1).dropLast(1)
                        } else {
                            tokens.drop(1)
                        }.map(String::toFloat))
                    }

                // faces can reference to three or more vertices in these notations:
                // f v1 v2 ... vn
                // f v1//vn1 v2//vn2 ... vn//vnn
                // f v1/vt1/vn1 v2/vt2/vn2 ... vn/vtn/vnn
                    "f" -> {
                        count++
                        val elements = tokens.drop(1).map { it.splitCustom('/') }

                        val vertices = elements.map { it[0].toInt() }
                        val uvs = elements.filter { it.size > 1 && it[1].isNotEmpty() }.map { it[1].toInt() }
                        val normals = elements.filter { it.size > 2 && it[2].isNotEmpty() }.map { it[2].toInt() }

                        val vertexHash = HashMap<Int, Triple<FloatArray, FloatArray, FloatArray>>()
                        for (i in ranges[vertices.size]!!) {
                            if(!vertexHash.containsKey(i)) {
                                val coordIndex = toBufferIndex(tmpV, vertices[i], 3, 0)
                                val coords = floatArrayOf(
                                    tmpV.getOrElse(coordIndex + 0, ::defaultHandler),
                                    tmpV.getOrElse(coordIndex + 1, ::defaultHandler),
                                    tmpV.getOrElse(coordIndex + 2, ::defaultHandler))

                                if (vertexBuffers[name]!!.first.position() == 0 || boundingBox == null) {
                                    boundingBox = floatArrayOf(coords[0], coords[0], coords[1], coords[1], coords[2], coords[2])
                                }

                                if (coords[0] < boundingBox!![0]) boundingBox!![0] = coords[0]
                                if (coords[1] < boundingBox!![2]) boundingBox!![2] = coords[1]
                                if (coords[2] < boundingBox!![4]) boundingBox!![4] = coords[2]

                                if (coords[0] > boundingBox!![1]) boundingBox!![1] = coords[0]
                                if (coords[1] > boundingBox!![3]) boundingBox!![3] = coords[1]
                                if (coords[2] > boundingBox!![5]) boundingBox!![5] = coords[2]

//                                vertexBuffers[name]!!.first.put(coords)

                                val norms = if (normals.size == vertices.size) {
                                    val normalIndex = toBufferIndex(tmpN, normals[i], 3, 0)

                                    floatArrayOf(
                                        tmpN.getOrElse(normalIndex + 0, ::defaultHandler),
                                        tmpN.getOrElse(normalIndex + 1, ::defaultHandler),
                                        tmpN.getOrElse(normalIndex + 2, ::defaultHandler))
                                } else {
                                    floatArrayOf()
                                }

                                val tc = if (uvs.size == vertices.size) {
                                    val uvIndex = toBufferIndex(tmpUV, uvs[i], 2, 0)
                                    floatArrayOf(
                                        tmpUV.getOrElse(uvIndex + 0, ::defaultHandler),
                                        tmpUV.getOrElse(uvIndex + 1, ::defaultHandler))
                                } else {
                                    floatArrayOf()
                                }

                                vertexHash.put(i, Triple(coords, norms, tc))
                            }
                        }

                        ranges[vertices.size]!!.map {
                            vertexBuffers[name]!!.first.put(vertexHash[it]!!.first)
                            vertexBuffers[name]!!.second.put(vertexHash[it]!!.second)
                            vertexBuffers[name]!!.third.put(vertexHash[it]!!.third)
                        }
                    }
                    "s" -> {
                        // TODO: Implement smooth shading across faces
                    }
                    "g" -> @Suppress("UNCHECKED_CAST") {
                        if (nbuffer.position() == 0) {
                            calculateNormals(vbuffer, nbuffer)
                        }

                        targetObject.vertices = vertexBuffers[name]!!.first
                        targetObject.normals = vertexBuffers[name]!!.second
                        targetObject.texcoords = vertexBuffers[name]!!.third

                        targetObject.vertices.flip()
                        targetObject.normals.flip()
                        targetObject.texcoords.flip()

                        vertexCount += targetObject.vertices.limit()
                        normalCount += targetObject.normals.limit()
                        uvCount += targetObject.texcoords.limit()

                        if (this is Node) {

                        }

                        // add new child mesh
                        if (this is Mesh) {
                            var child = Mesh()
                            child.name = tokens[1]
                            name = tokens[1]
                            if (!useMTL) {
                                child.material = Material()
                            }

                            boundingBox?.let {
                                (targetObject as Mesh).boundingBoxCoords = it.clone()
                            }
                            boundingBox = null

                            this.addChild(child)
                            targetObject = child
                        }

                    }
                    else -> {
                        if (!tokens[0].startsWith("#")) {
                            System.err.println("Unknown element: ${tokens.joinToString(" ")}")
                        }
                    }
                }
            }
        }

        val end = System.nanoTime()

        // recalculate normals if model did not supply them
        if (nbuffer.position() == 0) {
            calculateNormals(vbuffer, nbuffer)
        }

        targetObject.vertices = vertexBuffers[name]!!.first
        targetObject.normals = vertexBuffers[name]!!.second
        targetObject.texcoords = vertexBuffers[name]!!.third

        targetObject.vertices.flip()
        targetObject.normals.flip()
        targetObject.texcoords.flip()

        vertexCount += targetObject.vertices.limit()
        normalCount += targetObject.normals.limit()
        uvCount += targetObject.texcoords.limit()

        if (targetObject is Mesh) {
            if (boundingBox != null) {
                (targetObject as Mesh).boundingBoxCoords = boundingBox!!.clone()
            } else {
                (targetObject as Mesh).boundingBoxCoords = null
            }
        }

        System.out.println("Read ${vertexCount / vertexSize}/${normalCount / vertexSize}/${uvCount / texcoordSize} v/n/uv of model $name in ${(end - start) / 1e6} ms")
    }



    /**
     * Read the [Node]'s geometry from an STL file
     *
     * @param[filename] The filename to read from.
     */
    fun readFromSTL(filename: String) {
        var name: String = ""
        var vbuffer = ArrayList<Float>()
        var nbuffer = ArrayList<Float>()

        var boundingBox: FloatArray? = null

        var f = File(filename)
        if (!f.exists()) {
            System.out.println("Could not read from $filename, file does not exist.")

            return
        }

        val start = System.nanoTime()
        val lines = Files.lines(FileSystems.getDefault().getPath(filename))

        // This lambda is used in case the STL file is stored in ASCII format
        val readFromAscii = {
            System.out.println("Reading from ASCII STL file $filename")
            lines.forEach {
                line ->
                val tokens = line.trim().trimEnd().split(" ")
                when (tokens[0]) {
                    "solid" -> name = tokens.drop(1).joinToString(" ")
                    "vertex" -> with(tokens.drop(1)) {
                        val x = get(0).toFloat()
                        val y = get(1).toFloat()
                        val z = get(2).toFloat()

                        if (vbuffer.size == 0 || boundingBox == null) {
                            boundingBox = floatArrayOf(x, x, y, y, z, z)
                        }

                        if (x < boundingBox!![0]) boundingBox!![0] = x
                        if (y < boundingBox!![2]) boundingBox!![2] = y
                        if (z < boundingBox!![4]) boundingBox!![4] = z

                        if (x > boundingBox!![1]) boundingBox!![1] = x
                        if (y > boundingBox!![3]) boundingBox!![3] = y
                        if (z > boundingBox!![5]) boundingBox!![5] = z

                        vbuffer.add(x)
                        vbuffer.add(y)
                        vbuffer.add(z)
                    }
                    "facet" -> tokens.drop(2).forEach { nbuffer.add(it.toFloat()) }
                    "outer" -> {
                        (1..2).forEach { ((nbuffer.size - 3)..(nbuffer.size - 1)).forEach { nbuffer.add(nbuffer[it]) } }
                    }
                    "end" -> {
                    }
                    "endloop" -> {
                    }
                    "endfacet" -> {
                    }
                    "endsolid" -> {
                    }
                    else -> System.err.println("Unknown element: ${tokens.joinToString(" ")}")
                }
            }
        }

        // This lambda is used in case the STL file is stored binary
        val readFromBinary = {
            System.out.println("Reading from binary STL file $filename")

            val fis = FileInputStream(filename)
            val bis = BufferedInputStream(fis)
            var headerB: ByteArray = ByteArray(80)
            var sizeB: ByteArray = ByteArray(4)
            var buffer: ByteArray = ByteArray(12)
            var size: Int

            bis.read(headerB, 0, 80)
            bis.read(sizeB, 0, 4)

            size = ((sizeB[0].toInt() and 0xFF)
                or ((sizeB[1].toInt() and 0xFF) shl 8)
                or ((sizeB[2].toInt() and 0xFF) shl 16)
                or ((sizeB[3].toInt() and 0xFF) shl 24))

            fun readFloatFromInputStream(fis: BufferedInputStream): Float {
                var floatBuf = ByteArray(4)
                var bBuf: ByteBuffer

                fis.read(floatBuf, 0, 4)
                bBuf = ByteBuffer.wrap(floatBuf)
                bBuf.order(ByteOrder.LITTLE_ENDIAN)

                return bBuf.float
            }

            for (i in 1..size) {
                // surface normal
                val n1 = readFloatFromInputStream(bis)
                val n2 = readFloatFromInputStream(bis)
                val n3 = readFloatFromInputStream(bis)

                // vertices
                for (vertex in 1..3) {
                    val x = readFloatFromInputStream(bis)
                    val y = readFloatFromInputStream(bis)
                    val z = readFloatFromInputStream(bis)

                    if (vbuffer.size == 0 || boundingBox == null) {
                        boundingBox = floatArrayOf(x, x, y, y, z, z)
                    }

                    if (x < boundingBox!![0]) boundingBox!![0] = x
                    if (y < boundingBox!![2]) boundingBox!![2] = y
                    if (z < boundingBox!![4]) boundingBox!![4] = z

                    if (x > boundingBox!![1]) boundingBox!![1] = x
                    if (y > boundingBox!![3]) boundingBox!![3] = y
                    if (z > boundingBox!![5]) boundingBox!![5] = z

                    vbuffer.add(x)
                    vbuffer.add(y)
                    vbuffer.add(z)

                    nbuffer.add(n1)
                    nbuffer.add(n2)
                    nbuffer.add(n3)
                }

                bis.read(buffer, 0, 2)
            }

            fis.close()
        }

        var arr: CharArray = CharArray(6)
        f.reader().read(arr, 0, 6)

        // If the STL file starts with the string "solid", is must be a ASCII STL file,
        // otherwise it's assumed to be binary.
        if (arr.joinToString("").startsWith("solid ")) {
            readFromAscii
        } else {
            readFromBinary
        }.invoke()

        // normals are incomplete or missing, recalculate
        if (nbuffer.size != vbuffer.size) {
            System.err.println("Model does not supply surface normals, recalculating.")
            nbuffer.clear()

            var i = 0
            while (i < vbuffer.size) {
                val v1 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val v2 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val v3 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val a = v2 - v1
                val b = v3 - v1

                val n = a.cross(b).normalized

                (1..3).forEach {
                    nbuffer.add(n.x())
                    nbuffer.add(n.y())
                    nbuffer.add(n.z())
                }
            }
        }


        val end = System.nanoTime()
        System.out.println("Read ${vbuffer.size} vertices/${nbuffer.size} normals of model ${name} in ${(end - start) / 1e6} ms")

        vertices = ByteBuffer.allocateDirect(vbuffer.size * 4).asFloatBuffer()
        normals = ByteBuffer.allocateDirect(nbuffer.size * 4).asFloatBuffer()
        texcoords = ByteBuffer.allocateDirect(0).asFloatBuffer()
        indices = ByteBuffer.allocateDirect(0).asIntBuffer()

        vertices.put(vbuffer.toFloatArray())
        normals.put(nbuffer.toFloatArray())

        vertices.flip()
        normals.flip()

        if (this is Mesh && boundingBox != null) {
            System.err.println("BB of $name is ${boundingBox?.joinToString(",")}")
            this.boundingBoxCoords = boundingBox
        }
    }


    /**
     * Recalculates normals, assuming CCW winding order and taking
     * STL's facet storage format into account.
     */
    fun recalculateNormals() {
        var i = 0
        val normals = ArrayList<Float>()

        while (i < vertices.limit()) {
            val v1 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val v2 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val v3 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalized

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())
        }

        this.normals = BufferUtils.allocateFloatAndPut(normals.toFloatArray())
    }
}
