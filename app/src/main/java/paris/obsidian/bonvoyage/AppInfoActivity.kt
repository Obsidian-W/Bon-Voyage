package paris.obsidian.bonvoyage

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class AppInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_info)
        PlannerUi.insets(this)
        val about = intent.getBooleanExtra(SHOW_ABOUT, false)
        findViewById<MaterialToolbar>(R.id.infoToolbar).apply {
            setTitle(if (about) R.string.about_title else R.string.settings_title)
            setNavigationOnClickListener { finish() }
        }
        findViewById<View>(R.id.aboutCopy).isVisible = about
        findViewById<View>(R.id.privacyPolicy).setOnClickListener {
            PlannerUi.open(this) { getString(R.string.privacy_policy_url) }
        }
        val prefs = getPreferences(MODE_PRIVATE)
        val fish = findViewById<View>(R.id.fishBackground)
        findViewById<MaterialSwitch>(R.id.turboMode).apply {
            isVisible = !about
            isChecked = prefs.getBoolean(TURBO_MODE, false)
            fish.isVisible = !about && isChecked
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(TURBO_MODE, checked).apply()
                fish.isVisible = !about && checked
            }
        }
    }

    companion object {
        const val SHOW_ABOUT = "show_about"
        private const val TURBO_MODE = "turbo_mode"
    }
}
