package com.tyranor.next.ui.pages

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.tyranor.next.settings.HikarinagiAuthService

class HikarinagiOAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        HikarinagiAuthService.handleAuthorizationResponse(this, intent) { _, message ->
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
