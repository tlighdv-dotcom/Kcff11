package com.lightdev.khovault

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF0B0C0F)
private val SurfaceRaised = Color(0xFF15171B)
private val SurfaceSoft = Color(0xFF1C1F24)
private val TextPrimary = Color(0xFFF4F2EC)
private val Muted = Color(0xFF929397)
private val Accent = Color(0xFF9CDDE8)
private val Loss = Color(0xFFE6A4A0)
private val Hairline = Color(0xFF292C31)

data class DiamondEntry(
    val id: Long,
    val amount: Int,
    val title: String,
    val note: String,
    val timestamp: Long
)

enum class MembershipKind(
    val label: String,
    val instant: Int,
    val daily: Int,
    val days: Int,
    val total: Int
) {
    WEEKLY("Thẻ Tuần", 100, 50, 7, 450),
    MONTHLY("Thẻ Tháng", 500, 70, 30, 2600)
}

data class Membership(
    val id: Long,
    val kind: MembershipKind,
    val startEpochDay: Long,
    val claimedEpochDays: Set<Long>
) {
    fun isActive(today: LocalDate): Boolean =
        !today.isBefore(LocalDate.ofEpochDay(startEpochDay)) &&
            today.isBefore(LocalDate.ofEpochDay(startEpochDay).plusDays(kind.days.toLong()))

    fun hasClaimed(today: LocalDate): Boolean = today.toEpochDay() in claimedEpochDays
}

data class AppSnapshot(
    val entries: List<DiamondEntry> = emptyList(),
    val memberships: List<Membership> = emptyList(),
    val goal: Int = 5000
)

private class DiamondStore(context: Context) {
    private val prefs = context.getSharedPreferences("diamond_vault", Context.MODE_PRIVATE)

    fun load(): AppSnapshot = runCatching {
        val entriesJson = JSONArray(prefs.getString("entries", "[]"))
        val entries = buildList {
            for (i in 0 until entriesJson.length()) {
                val item = entriesJson.getJSONObject(i)
                add(
                    DiamondEntry(
                        id = item.getLong("id"),
                        amount = item.getInt("amount"),
                        title = item.getString("title"),
                        note = item.optString("note"),
                        timestamp = item.getLong("timestamp")
                    )
                )
            }
        }.sortedByDescending { it.timestamp }

        val membershipsJson = JSONArray(prefs.getString("memberships", "[]"))
        val memberships = buildList {
            for (i in 0 until membershipsJson.length()) {
                val item = membershipsJson.getJSONObject(i)
                val claimsJson = item.optJSONArray("claims") ?: JSONArray()
                val claims = buildSet {
                    for (j in 0 until claimsJson.length()) add(claimsJson.getLong(j))
                }
                add(
                    Membership(
                        id = item.getLong("id"),
                        kind = MembershipKind.valueOf(item.getString("kind")),
                        startEpochDay = item.getLong("start"),
                        claimedEpochDays = claims
                    )
                )
            }
        }
        AppSnapshot(entries, memberships, prefs.getInt("goal", 5000))
    }.getOrElse { AppSnapshot() }

    private fun save(snapshot: AppSnapshot): AppSnapshot {
        val entriesJson = JSONArray().apply {
            snapshot.entries.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("amount", entry.amount)
                    put("title", entry.title)
                    put("note", entry.note)
                    put("timestamp", entry.timestamp)
                })
            }
        }
        val membershipsJson = JSONArray().apply {
            snapshot.memberships.forEach { membership ->
                put(JSONObject().apply {
                    put("id", membership.id)
                    put("kind", membership.kind.name)
                    put("start", membership.startEpochDay)
                    put("claims", JSONArray(membership.claimedEpochDays.toList()))
                })
            }
        }
        prefs.edit {
            putString("entries", entriesJson.toString())
            putString("memberships", membershipsJson.toString())
            putInt("goal", snapshot.goal)
        }
        return snapshot
    }

    fun add(snapshot: AppSnapshot, amount: Int, title: String, note: String): AppSnapshot =
        save(
            snapshot.copy(
                entries = listOf(
                    DiamondEntry(
                        id = System.nanoTime(),
                        amount = amount,
                        title = title,
                        note = note.trim(),
                        timestamp = System.currentTimeMillis()
                    )
                ) + snapshot.entries
            )
        )

    fun remove(snapshot: AppSnapshot, id: Long): AppSnapshot =
        save(snapshot.copy(entries = snapshot.entries.filterNot { it.id == id }))

    fun setGoal(snapshot: AppSnapshot, goal: Int): AppSnapshot = save(snapshot.copy(goal = goal))

    fun activate(snapshot: AppSnapshot, kind: MembershipKind, today: LocalDate): AppSnapshot {
        if (snapshot.memberships.any { it.kind == kind && it.isActive(today) }) return snapshot
        val membership = Membership(System.nanoTime(), kind, today.toEpochDay(), emptySet())
        val withMembership = snapshot.copy(memberships = listOf(membership) + snapshot.memberships)
        return add(withMembership, kind.instant, "Kích hoạt ${kind.label}", "Nhận ngay ${kind.instant} KC")
    }

    fun claim(snapshot: AppSnapshot, membershipId: Long, today: LocalDate): AppSnapshot {
        val membership = snapshot.memberships.firstOrNull { it.id == membershipId } ?: return snapshot
        if (!membership.isActive(today) || membership.hasClaimed(today)) return snapshot
        val updated = snapshot.copy(
            memberships = snapshot.memberships.map {
                if (it.id == membershipId) it.copy(claimedEpochDays = it.claimedEpochDays + today.toEpochDay()) else it
            }
        )
        return add(updated, membership.kind.daily, "Nhận ${membership.kind.label}", "Phần thưởng ngày")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DiamondVaultApp() }
    }
}

private enum class LineIcon { HOME, HISTORY, CARD, PLUS, MINUS, CLOSE }

private enum class AppTab(val label: String, val icon: LineIcon) {
    HOME("Trang chủ", LineIcon.HOME),
    HISTORY("Lịch sử", LineIcon.HISTORY),
    CARDS("Thẻ", LineIcon.CARD)
}

@Composable
private fun DiamondVaultApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { DiamondStore(context) }
    var snapshot by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var addAsIncome by remember { mutableStateOf(true) }
    var showGoal by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = TextPrimary,
            background = Ink,
            surface = SurfaceRaised,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Scaffold(
                containerColor = Ink,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = { AppHeader() },
                bottomBar = { PremiumNavigation(tab = tab, onSelect = { tab = it }) }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (tab) {
                        AppTab.HOME -> Dashboard(
                            snapshot = snapshot,
                            onGoalClick = { showGoal = true },
                            onAdd = { income ->
                                addAsIncome = income
                                showAdd = true
                            },
                            onClaim = { snapshot = store.claim(snapshot, it, LocalDate.now()) },
                            onSeeCards = { tab = AppTab.CARDS }
                        )
                        AppTab.HISTORY -> History(
                            entries = snapshot.entries,
                            onAdd = {
                                addAsIncome = true
                                showAdd = true
                            },
                            onRemove = { snapshot = store.remove(snapshot, it) }
                        )
                        AppTab.CARDS -> Memberships(
                            snapshot = snapshot,
                            onActivate = { snapshot = store.activate(snapshot, it, LocalDate.now()) },
                            onClaim = { snapshot = store.claim(snapshot, it, LocalDate.now()) }
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AddEntryDialog(
                initialIncome = addAsIncome,
                onDismiss = { showAdd = false },
                onSave = { amount, title, note ->
                    snapshot = store.add(snapshot, amount, title, note)
                    showAdd = false
                }
            )
        }
        if (showGoal) {
            GoalDialog(
                current = snapshot.goal,
                onDismiss = { showGoal = false },
                onSave = {
                    snapshot = store.setGoal(snapshot, it)
                    showGoal = false
                }
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiamondMark(24.dp)
        Spacer(Modifier.width(10.dp))
        Text("KIM CƯƠNG", fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(CircleShape)
                .background(SurfaceRaised)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("OFFLINE", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PremiumNavigation(tab: AppTab, onSelect: (AppTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceRaised)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTab.entries.forEach { item ->
                val selected = tab == item
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) SurfaceSoft else Color.Transparent)
                        .clickable { onSelect(item) }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLineIcon(item.icon, if (selected) Accent else Muted, 19.dp)
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    snapshot: AppSnapshot,
    onGoalClick: () -> Unit,
    onAdd: (Boolean) -> Unit,
    onClaim: (Long) -> Unit,
    onSeeCards: () -> Unit
) {
    val balance = DiamondMath.balance(snapshot.entries)
    val received = DiamondMath.received(snapshot.entries)
    val spent = DiamondMath.spent(snapshot.entries)
    val active = snapshot.memberships.filter { it.isActive(LocalDate.now()) }
    val streak = remember(snapshot.entries) { streakDays(snapshot.entries) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { BalanceHero(balance) }
        item { QuickActions(onAdd) }
        item { SummaryStrip(received, spent, streak) }
        item { GoalCard(balance, snapshot.goal, onGoalClick) }
        item {
            SectionTitle("Hôm nay", if (active.isEmpty()) "Xem thẻ" else null, onSeeCards)
        }
        if (active.isEmpty()) {
            item {
                EmptyCard(
                    title = "Chưa có thẻ hoạt động",
                    message = "Theo dõi Thẻ Tuần hoặc Thẻ Tháng và ghi nhận phần thưởng mỗi ngày.",
                    action = "Khám phá thẻ",
                    onClick = onSeeCards
                )
            }
        } else {
            items(active, key = { it.id }) { membership ->
                CompactClaimCard(membership, onClaim)
            }
        }
        item { SectionTitle("Gần đây") }
        if (snapshot.entries.isEmpty()) {
            item { EmptyCard("Chưa có dữ liệu", "Ghi lần nhận hoặc chi Kim Cương đầu tiên.") }
        } else {
            items(snapshot.entries.take(4), key = { it.id }) { EntryRow(it) }
        }
    }
}

@Composable
private fun BalanceHero(balance: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("SỐ DƯ KHẢ DỤNG", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatNumber(balance), fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            Spacer(Modifier.width(9.dp))
            Text("KC", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
        }
    }
}

@Composable
private fun QuickActions(onAdd: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionPill("Ghi nhận", LineIcon.PLUS, true, Modifier.weight(1f)) { onAdd(true) }
        ActionPill("Đã chi", LineIcon.MINUS, false, Modifier.weight(1f)) { onAdd(false) }
    }
}

@Composable
private fun ActionPill(label: String, icon: LineIcon, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) Accent else SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLineIcon(icon, if (primary) Ink else TextPrimary, 17.dp)
        Spacer(Modifier.width(9.dp))
        Text(label, color = if (primary) Ink else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryStrip(received: Int, spent: Int, streak: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryItem("ĐÃ NHẬN", formatNumber(received), Modifier.weight(1f))
        SummaryItem("ĐÃ CHI", formatNumber(spent), Modifier.weight(1f))
        SummaryItem("CHUỖI NGÀY", streak.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = .8.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoalCard(balance: Int, goal: Int, onClick: () -> Unit) {
    val progress = DiamondMath.goalProgress(balance, goal)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(SurfaceRaised),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Mục tiêu", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${formatNumber(balance.coerceAtLeast(0))} / ${formatNumber(goal)} KC", color = Muted, fontSize = 12.sp)
                }
                Text("${(progress * 100).roundToInt()}%", color = Accent, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            }
            Spacer(Modifier.height(15.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = Accent,
                trackColor = Hairline,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp)
        if (action != null) Text(action, color = Accent, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick).padding(4.dp))
    }
}

@Composable
private fun EmptyCard(title: String, message: String, action: String? = null, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SurfaceRaised), border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DiamondMark(26.dp, muted = true)
            Spacer(Modifier.height(13.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (action != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun CompactClaimCard(membership: Membership, onClaim: (Long) -> Unit) {
    val today = LocalDate.now()
    val claimed = membership.hasClaimed(today)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SurfaceRaised), border = BorderStroke(1.dp, Hairline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(SurfaceSoft), contentAlignment = Alignment.Center) {
                Text("+${membership.kind.daily}", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(membership.kind.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${membership.claimedEpochDays.size}/${membership.kind.days} ngày đã nhận", color = Muted, fontSize = 12.sp)
            }
            Button(
                onClick = { onClaim(membership.id) },
                enabled = !claimed,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink, disabledContainerColor = SurfaceSoft, disabledContentColor = Muted),
                contentPadding = PaddingValues(horizontal = 14.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (claimed) "Đã nhận" else "Nhận", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun History(entries: List<DiamondEntry>, onAdd: () -> Unit, onRemove: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Lịch sử", fontWeight = FontWeight.Light, fontSize = 30.sp)
                    Text("Chỉ lưu trên thiết bị", color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(SurfaceRaised).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    AppLineIcon(LineIcon.PLUS, Accent, 18.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (entries.isEmpty()) item { EmptyCard("Chưa có giao dịch", "Các lần nạp, chi và nhận thẻ sẽ xuất hiện tại đây.") }
        items(entries, key = { it.id }) { entry ->
            EntryRow(entry, onRemove)
            HorizontalDivider(color = Hairline)
        }
    }
}

@Composable
private fun EntryRow(entry: DiamondEntry, onRemove: ((Long) -> Unit)? = null) {
    val positive = entry.amount >= 0
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
            AppLineIcon(if (positive) LineIcon.PLUS else LineIcon.MINUS, if (positive) Accent else Loss, 16.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatDate(entry.timestamp), entry.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (positive) "+" else "") + formatNumber(entry.amount),
                color = if (positive) Accent else Loss,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text("KC", color = Muted, fontSize = 9.sp)
        }
        if (onRemove != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(28.dp).clickable { onRemove(entry.id) }, contentAlignment = Alignment.Center) {
                AppLineIcon(LineIcon.CLOSE, Muted, 14.dp)
            }
        }
    }
}

@Composable
private fun Memberships(snapshot: AppSnapshot, onActivate: (MembershipKind) -> Unit, onClaim: (Long) -> Unit) {
    val today = LocalDate.now()
    val active = snapshot.memberships.filter { it.isActive(today) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 12.dp, 22.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Thẻ của bạn", fontWeight = FontWeight.Light, fontSize = 30.sp)
            Text("Theo dõi thủ công · không liên kết tài khoản", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
        }
        items(MembershipKind.entries) { kind ->
            val current = active.firstOrNull { it.kind == kind }
            MembershipPlanCard(kind, current, onActivate, onClaim)
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(SurfaceRaised)) {
                Column(Modifier.padding(16.dp)) {
                    Text("THÔNG TIN", color = Accent, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.3.sp)
                    Text(
                        "Mức 450/2.600 KC là cấu hình tham khảo phổ biến. Quyền lợi thực tế có thể được Garena thay đổi; hãy kiểm tra trong game trước khi mua.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MembershipPlanCard(
    kind: MembershipKind,
    membership: Membership?,
    onActivate: (MembershipKind) -> Unit,
    onClaim: (Long) -> Unit
) {
    val today = LocalDate.now()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(SurfaceRaised), border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(kind.label.uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text("${formatNumber(kind.total)} KC", fontSize = 27.sp, fontWeight = FontWeight.Light)
                }
                DiamondMark(32.dp, muted = kind == MembershipKind.MONTHLY)
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Hairline)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlanFact("NHẬN NGAY", "${kind.instant} KC")
                PlanFact("MỖI NGÀY", "+${kind.daily} KC")
                PlanFact("THỜI HẠN", "${kind.days} ngày")
            }
            Spacer(Modifier.height(18.dp))
            if (membership == null) {
                Button(
                    onClick = { onActivate(kind) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (kind == MembershipKind.WEEKLY) Accent else TextPrimary, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Bắt đầu theo dõi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            } else {
                val claimed = membership.hasClaimed(today)
                val progress = membership.claimedEpochDays.size.toFloat() / kind.days
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = Accent,
                    trackColor = Hairline
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Đã nhận ${membership.claimedEpochDays.size}/${kind.days} ngày", color = Muted, fontSize = 12.sp)
                    Button(
                        onClick = { onClaim(membership.id) },
                        enabled = !claimed,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink, disabledContainerColor = SurfaceSoft, disabledContentColor = Muted),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (claimed) "Đã nhận" else "+${kind.daily} KC", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun PlanFact(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .8.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryDialog(initialIncome: Boolean, onDismiss: () -> Unit, onSave: (Int, String, String) -> Unit) {
    var isIncome by remember(initialIncome) { mutableStateOf(initialIncome) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = amount.toIntOrNull()?.takeIf { it > 0 }
    val presets = listOf(100, 310, 520, 1060, 2180)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (isIncome) "Ghi nhận Kim Cương" else "Kim Cương đã chi", fontWeight = FontWeight.Medium, fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton("Ghi nhận", isIncome, Modifier.weight(1f)) { isIncome = true }
                    ToggleButton("Đã chi", !isIncome, Modifier.weight(1f)) { isIncome = false }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(7) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Số Kim Cương") },
                    suffix = { Text("KC") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(presets) { value ->
                        OutlinedButton(onClick = { amount = value.toString() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(formatNumber(value), fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú (không bắt buộc)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val signed = if (isIncome) parsed!! else -parsed!!
                    onSave(signed, if (isIncome) "Nạp / nhận Kim Cương" else "Chi Kim Cương", note)
                },
                enabled = parsed != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Lưu", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(Accent, Ink) else ButtonDefaults.outlinedButtonColors(contentColor = Muted)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, colors = colors, contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(12.dp)) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, colors = colors, border = BorderStroke(1.dp, Hairline), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(12.dp)) { Text(label, fontSize = 12.sp) }
    }
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    val parsed = value.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Mục tiêu Kim Cương", fontWeight = FontWeight.Medium, fontSize = 20.sp) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(8) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mục tiêu") },
                suffix = { Text("KC") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { Button(onClick = { onSave(parsed!!) }, enabled = parsed != null, colors = ButtonDefaults.buttonColors(Accent, Ink), shape = RoundedCornerShape(12.dp)) { Text("Cập nhật") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun DiamondMark(size: androidx.compose.ui.unit.Dp, muted: Boolean = false) {
    val primary = if (muted) Muted.copy(.55f) else Accent
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val diamond = Path().apply {
            moveTo(w * .12f, h * .34f)
            lineTo(w * .32f, h * .12f)
            lineTo(w * .68f, h * .12f)
            lineTo(w * .88f, h * .34f)
            lineTo(w * .50f, h * .90f)
            close()
        }
        drawPath(diamond, primary.copy(.08f))
        drawPath(diamond, primary, style = Stroke(width = w * .055f))
        drawLine(primary.copy(.72f), Offset(w * .12f, h * .34f), Offset(w * .88f, h * .34f), w * .04f)
        drawLine(primary.copy(.65f), Offset(w * .32f, h * .12f), Offset(w * .50f, h * .90f), w * .035f)
        drawLine(primary.copy(.65f), Offset(w * .68f, h * .12f), Offset(w * .50f, h * .90f), w * .035f)
    }
}

@Composable
private fun AppLineIcon(icon: LineIcon, color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * .085f
        when (icon) {
            LineIcon.HOME -> {
                val path = Path().apply {
                    moveTo(w * .14f, h * .46f)
                    lineTo(w * .50f, h * .16f)
                    lineTo(w * .86f, h * .46f)
                    lineTo(w * .78f, h * .46f)
                    lineTo(w * .78f, h * .84f)
                    lineTo(w * .58f, h * .84f)
                    lineTo(w * .58f, h * .61f)
                    lineTo(w * .42f, h * .61f)
                    lineTo(w * .42f, h * .84f)
                    lineTo(w * .22f, h * .84f)
                    lineTo(w * .22f, h * .46f)
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            LineIcon.HISTORY -> {
                drawLine(color, Offset(w * .18f, h * .26f), Offset(w * .82f, h * .26f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .18f, h * .50f), Offset(w * .70f, h * .50f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .18f, h * .74f), Offset(w * .58f, h * .74f), stroke, StrokeCap.Round)
            }
            LineIcon.CARD -> {
                drawRoundRect(color, Offset(w * .12f, h * .22f), androidx.compose.ui.geometry.Size(w * .76f, h * .56f), androidx.compose.ui.geometry.CornerRadius(w * .10f), style = Stroke(stroke))
                drawLine(color, Offset(w * .12f, h * .42f), Offset(w * .88f, h * .42f), stroke)
            }
            LineIcon.PLUS -> {
                drawLine(color, Offset(w * .50f, h * .19f), Offset(w * .50f, h * .81f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .19f, h * .50f), Offset(w * .81f, h * .50f), stroke, StrokeCap.Round)
            }
            LineIcon.MINUS -> drawLine(color, Offset(w * .19f, h * .50f), Offset(w * .81f, h * .50f), stroke, StrokeCap.Round)
            LineIcon.CLOSE -> {
                drawLine(color, Offset(w * .23f, h * .23f), Offset(w * .77f, h * .77f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .77f, h * .23f), Offset(w * .23f, h * .77f), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(value)

private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM • HH:mm"))

private fun streakDays(entries: List<DiamondEntry>): Int {
    val dates = entries.map {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSet()
    if (dates.isEmpty()) return 0
    var cursor = LocalDate.now()
    if (cursor !in dates) cursor = cursor.minusDays(1)
    var streak = 0
    while (cursor in dates) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}
