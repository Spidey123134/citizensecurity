package com.example.citizensecurity.help

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Does not launch a dialer or call anybody: checks the exact production intent contract. */
@RunWith(AndroidJUnit4::class)
class HelpDialerTest {
    @Test fun emergencyTargetsOpenTheDialerWithoutCallingSendingExtrasOrRequiringLocation() {
        listOf("911", "089").forEach { number ->
            val intent = HelpDialer.intent(number)
            assertEquals(Intent.ACTION_DIAL, intent.action)
            assertEquals("tel", intent.data?.scheme)
            assertEquals(number, intent.data?.schemeSpecificPart)
            assertNull(intent.data?.fragment)
            assertNull(intent.extras)
            assertNull(intent.component)
        }
    }

    @Test fun canonicalInternationalContactKeepsASingleTelephoneTarget() {
        val number = "+525512345678"
        val intent = HelpDialer.intent(number)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(number, intent.data?.schemeSpecificPart)
        assertNull(intent.clipData)
    }

    @Test fun unsafeOrUnnormalizedTargetsCannotOpenAnIntent() {
        listOf("tel:5512345678", "5512345678;911", "5512345678,911", "*5512345678", "55 1234 5678",
            "5512345678\n", "++525512345678", "911#", "089;911").forEach { invalid ->
            try { HelpDialer.intent(invalid); fail("Destino inseguro aceptado.") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
