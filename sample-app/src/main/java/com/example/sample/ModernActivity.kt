package com.example.sample

import android.app.Activity
import android.view.KeyEvent

/** Nothing to report here: a class that only shares the method name, and a different key. */
class ModernActivity : Activity() {
    fun onBackPressed(reason: String) {
        finish()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = keyCode == KeyEvent.KEYCODE_HOME
}

class Scene {
    fun onBackPressed() {}
}
