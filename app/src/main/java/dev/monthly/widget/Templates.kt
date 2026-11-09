package dev.monthly.widget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 마감일에서 거꾸로 계산하는 체크리스트 초안. 만든 뒤에는 자유롭게 고치고 지울 수 있습니다.
 * offset = 마감일 기준 며칠 전. 남은 기간이 짧으면 비율대로 압축합니다.
 */
object Templates {
    class Step(val title: String, val offset: Int)

    val ZINE = listOf(
        Step("플롯·콘티 정리", -60),
        Step("초고 완성", -35),
        Step("퇴고", -24),
        Step("표지·내지 디자인", -21),
        Step("조판·교정", -17),
        Step("인쇄소 입고 (진짜 마감)", -14),
        Step("통판·행사 준비", -7),
        Step("발행·행사 당일", 0),
    )

    val COMMU = listOf(
        Step("세계관·스토리 정리", -60),
        Step("시스템·수치 확정", -45),
        Step("공지·신청서 양식 만들기", -35),
        Step("홍보 시작", -30),
        Step("모집 마감", -18),
        Step("합격 발표", -14),
        Step("오픈 준비 (공지·채널 세팅)", -3),
        Step("커뮤 오픈", 0),
    )

    fun build(steps: List<Step>, project: Project, today: LocalDate, startId: Long): List<Item> {
        val avail = ChronoUnit.DAYS.between(today, project.deadline)
        val span = -(steps.minOf { it.offset })
        val factor = if (avail > 0 && avail < span) avail.toDouble() / span else 1.0
        return steps.mapIndexed { i, s ->
            var d = project.deadline.plusDays(Math.round(s.offset * factor))
            if (d.isBefore(today)) d = today
            Item(startId + i, d, s.title, ItemType.TODO, false, project.color, "", false, project.id)
        }
    }
}
