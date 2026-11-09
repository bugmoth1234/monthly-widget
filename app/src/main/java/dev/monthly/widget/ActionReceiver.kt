package dev.monthly.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 위젯 버튼(‹ ›, ○ 체크)과 자정 갱신을 처리하는 receiver.
 * exported=false라서 다른 앱은 이 동작을 보낼 수 없고, 이 앱이 만든 PendingIntent만 들어옵니다.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV -> {
                Store.setOffset(ctx, (Store.getOffset(ctx) - 1).coerceIn(-240, 240))
                WidgetRenderer.updateAll(ctx)
            }
            ACTION_NEXT -> {
                Store.setOffset(ctx, (Store.getOffset(ctx) + 1).coerceIn(-240, 240))
                WidgetRenderer.updateAll(ctx)
            }
            ACTION_TOGGLE -> {
                val id = intent.getLongExtra(EXTRA_ID, 0)
                val items = Store.load(ctx)
                val i = items.indexOfFirst { it.id == id }
                if (i >= 0) {
                    items[i] = items[i].copy(done = !items[i].done)
                    Store.save(ctx, items)
                }
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
        const val ACTION_TOGGLE = "dev.monthly.widget.TOGGLE"
        const val EXTRA_ID = "id"
    }
}
