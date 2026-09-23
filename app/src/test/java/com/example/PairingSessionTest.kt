package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.SessionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PairingSessionTest {
  @Test fun `pairing survives recreation without rotating or extending expiry`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val session = SessionManager.PairingSession("ABC-123", 900_000)
    SessionManager(context).savePairing("officer", "group-a", session)
    val restored = SessionManager(context).loadPairing("officer", "group-a")!!
    assertEquals(session, restored)
    assertEquals(600, restored.secondsRemaining(300_000))
    assertEquals(0, restored.secondsRemaining(900_001))
  }

  @Test fun `pairing cannot leak across groups or officers`() {
    val manager = SessionManager(ApplicationProvider.getApplicationContext<Context>())
    manager.savePairing("officer-a", "group-a", SessionManager.PairingSession("ABC-123", 900_000))
    assertNull(manager.loadPairing("officer-b", "group-a"))
    assertNull(manager.loadPairing("officer-a", "group-b"))
  }

  @Test fun `reopening never replaces a code even after expiry`() {
    val manager = SessionManager(ApplicationProvider.getApplicationContext<Context>())
    val expired = SessionManager.PairingSession("OLD-123", 1)
    manager.savePairing("officer", "expired-group", expired)
    assertEquals(expired, manager.pairingForConnection("officer", "expired-group"))
    val replacement = manager.pairingForConnection("officer", "expired-group", forceNew = true)
    assertTrue(replacement.expiresAt > System.currentTimeMillis())
    assertEquals(replacement, manager.pairingForConnection("officer", "expired-group"))
  }
}
