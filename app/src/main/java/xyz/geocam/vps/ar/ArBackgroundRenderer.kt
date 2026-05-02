package xyz.geocam.vps.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal renderer that exists only to provide ARCore with a valid camera
 * texture. Draws the camera background full-screen so the user sees the
 * preview while the map sits above it.
 */
class ArBackgroundRenderer(
    private val sessionProvider: () -> Session?,
    private val onFrame: () -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var textureId: Int = -1
        private set

    private var program: Int = 0
    private var positionAttrib: Int = 0
    private var texCoordAttrib: Int = 0
    private val quadCoords: FloatBuffer
    private val texCoords: FloatBuffer = FloatBuffer.allocate(8)

    init {
        val coords = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f,
        )
        quadCoords = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { it.put(coords); it.position(0) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        textureId = GlUtils.createCameraTexture()

        val vsh = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }
        """.trimIndent()
        val fsh = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() { gl_FragColor = texture2D(u_Texture, v_TexCoord); }
        """.trimIndent()
        program = compileProgram(vsh, fsh)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")

        sessionProvider()?.setCameraTextureName(textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        sessionProvider()?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = sessionProvider() ?: return
        if (textureId == -1) return

        runCatching {
            val frame = session.update()
            if (frame.hasDisplayGeometryChanged()) {
                val src = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
                val dst = FloatArray(8)
                frame.transformCoordinates2d(
                    com.google.ar.core.Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    src,
                    com.google.ar.core.Coordinates2d.TEXTURE_NORMALIZED,
                    dst,
                )
                texCoords.position(0)
                texCoords.put(dst)
                texCoords.position(0)
            } else if (texCoords.position() == 0 && texCoords.limit() == texCoords.capacity()) {
                texCoords.put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
                texCoords.position(0)
            }

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glEnableVertexAttribArray(positionAttrib)
            GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
            GLES20.glEnableVertexAttribArray(texCoordAttrib)
            GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionAttrib)
            GLES20.glDisableVertexAttribArray(texCoordAttrib)

            onFrame()
        }
    }

    private fun compileProgram(vsh: String, fsh: String): Int {
        val v = loadShader(GLES20.GL_VERTEX_SHADER, vsh)
        val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fsh)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }
}
