package io.github.iammaplezeng.xtgytmediasaver

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

class HookContractTest {
    @Test fun entryRemainsPublicAndConstructible() {
        val constructor = XTGMediaSaverModule::class.java.getConstructor()
        assertTrue(Modifier.isPublic(constructor.modifiers))
        assertNotNull(constructor.newInstance())
    }
    @Test fun activationRemainsJvmStaticWithOriginalName() {
        val method = ModuleStatus::class.java.getDeclaredMethod("isActivated")
        assertTrue(Modifier.isStatic(method.modifiers)); assertEquals(java.lang.Boolean.TYPE, method.returnType)
        assertEquals(false, method.invoke(null))
    }
    @Test fun existingPreferencesStayCompatible() {
        assertEquals("xtg_prefs", ModulePrefs.PREFS_NAME)
        assertEquals("x_enabled", ModulePrefs.KEY_X_ENABLED)
        assertEquals("x_video_enabled", ModulePrefs.KEY_X_VIDEO)
        assertEquals("x_all_images_enabled", ModulePrefs.KEY_X_ALL_IMAGES)
        assertEquals("tg_enabled", ModulePrefs.KEY_TG_ENABLED)
        assertEquals("tg_save_restricted_enabled", ModulePrefs.KEY_TG_SAVE_RESTRICTED)
    }
}
