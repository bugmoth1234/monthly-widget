package dev.monthly.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

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

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_PREV -> {
                Store.setOffset(ctx, Store.getOffset(ctx) - 1)
                WidgetRenderer.updateAll(ctx)
            }
            ACTION_NEXT -> {
                Store.setOffset(ctx, Store.getOffset(ctx) + 1)
                WidgetRenderer.updateAll(ctx)
            }
            ACTION_MIDNIGHT -> {
                Store.setOffset(ctx, 0)
                WidgetRenderer.updateAll(ctx)
                WidgetRenderer.scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_PREV = "dev.monthly.widget.PREV"
        const val ACTION_NEXT = "dev.monthly.widget.NEXT"
        const val ACTION_MIDNIGHT = "dev.monthly.widget.MIDNIGHT"
    }
}
