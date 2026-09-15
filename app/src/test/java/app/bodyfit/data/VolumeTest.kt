package app.bodyfit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {

    @Test
    fun `below a litre stays in millilitres`() {
        assertEquals("250 ml", Volume.format(250))
        assertEquals("999 ml", Volume.format(999))
    }

    @Test
    fun `a whole number of litres drops the decimals`() {
        assertEquals("1 L", Volume.format(1_000))
        assertEquals("3 L", Volume.format(3_000))
    }

    @Test
    fun `hundreds show one decimal and the rest show two`() {
        assertEquals("1.5 L", Volume.format(1_500))
        assertEquals("1.2 L", Volume.format(1_200))
        assertEquals("1.25 L", Volume.format(1_250))
    }

    @Test
    fun `unit and amount agree at the switch`() {
        assertEquals("ml", Volume.unit(999.0))
        assertEquals("L", Volume.unit(1_000.0))
        assertEquals("999", Volume.amount(999.0))
        assertEquals("1", Volume.amount(1_000.0))
    }
}
