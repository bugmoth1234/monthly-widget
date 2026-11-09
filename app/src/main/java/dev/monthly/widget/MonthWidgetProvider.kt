package dev.monthly.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** 시스템이 보내는 위젯 갱신만 처리. 버튼 동작은 공개되지 않은 ActionReceiver가 맡음. */
class MonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.updateAll(ctx)
        WidgetRenderer.scheduleMidnight(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetRenderer.updateAll(ctx) // 위젯 크기를 바꾸면 그림도 다시 맞춤
    }

    override fun onEnabled(ctx: Context) {
        WidgetRenderer.scheduleMidnight(ctx)
    }
}
