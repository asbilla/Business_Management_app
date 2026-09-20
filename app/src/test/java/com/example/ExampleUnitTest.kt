package com.example

import com.example.data.model.AppointmentSettings
import com.example.data.pref.AppPreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAppointmentSlotGeneration() {
    val settings = AppointmentSettings(
      bookingEnabled = true,
      slotDurationMinutes = 30,
      startHour = 9,
      startMinute = 0,
      endHour = 17,
      endMinute = 0,
      bufferMinutes = 0
    )
    val slots = settings.generateSlots()
    assertFalse(slots.isEmpty())
    assertEquals("09:00 AM", slots.first())
    assertEquals("04:30 PM", slots.last())
    // 9:00 to 17:00 is 8 hours = 16 30-min slots
    assertEquals(16, slots.size)
  }

  @Test
  fun testAppointmentSlotGenerationDisabled() {
    val settings = AppointmentSettings(bookingEnabled = false)
    val slots = settings.generateSlots()
    assertTrue(slots.isEmpty())
  }
}
