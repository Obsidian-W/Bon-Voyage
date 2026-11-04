package paris.obsidian.bonvoyage

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar

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
    }

    companion object {
        const val SHOW_ABOUT = "show_about"
    }
}
