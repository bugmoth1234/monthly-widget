package dev.monthly.widget

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 아래 탭 (일정 / 프로젝트 / 꾸미기) */
object Nav {
    fun setup(act: AppCompatActivity, selected: Int) {
        val nav = act.findViewById<BottomNavigationView>(R.id.bottom_nav) ?: return
        nav.selectedItemId = selected
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selected) return@setOnItemSelectedListener true
            val cls = when (item.itemId) {
                R.id.nav_schedule -> MainActivity::class.java
                R.id.nav_projects -> ProjectsActivity::class.java
                else -> DecorActivity::class.java
            }
            act.startActivity(
                Intent(act, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            @Suppress("DEPRECATION")
            act.overridePendingTransition(0, 0)
            false // 이 화면의 선택 표시는 그대로 둠
        }
    }
}

/** 머티리얼 달력으로 날짜 고르기 */
object DatePick {
    fun show(act: AppCompatActivity, initial: LocalDate, title: String, onPick: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { ms ->
            onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(act.supportFragmentManager, "date")
    }
}
