package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionPhotosTest {
    @Test
    fun microVideoAttribute() {
        val xmp = """<rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoVersion="1" GCamera:MicroVideoOffset="2481334"/>"""
        assertEquals(2481334L, motionVideoLength(xmp))
    }

    @Test
    fun microVideoElement() {
        val xmp = "<GCamera:MicroVideo>1</GCamera:MicroVideo><GCamera:MicroVideoOffset> 1500 </GCamera:MicroVideoOffset>"
        assertEquals(1500L, motionVideoLength(xmp))
    }

    @Test
    fun motionPhotoContainer() {
        val xmp = """
            <rdf:Description Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1">
              <Container:Directory><rdf:Seq>
                <rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>
                <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="MotionPhoto" Item:Mime="video/mp4" Item:Length="3000000" Item:Padding="16"/></rdf:li>
              </rdf:Seq></Container:Directory>
            </rdf:Description>
        """.trimIndent()
        assertEquals(3000016L, motionVideoLength(xmp))
    }

    @Test
    fun ordinaryPhotos() {
        assertNull(motionVideoLength("""<rdf:Description xmp:CreatorTool="Camera"/>"""))
        assertNull(motionVideoLength("""<rdf:Description GCamera:MicroVideoOffset="0"/>"""))
        assertNull(motionVideoLength("""<Container:Directory><Container:Item Item:Mime="image/jpeg" Item:Length="5"/></Container:Directory>"""))
    }
}
