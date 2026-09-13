package com.lightdev.khovault

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
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

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101D2E)
private val PanelSoft = Color(0xFF16263A)
private val Cyan = Color(0xFF29D8FF)
private val Gold = Color(0xFFF7C948)
private val Mint = Color(0xFF64E6A5)
private val Coral = Color(0xFFFF7085)
private val Muted = Color(0xFF91A6BD)

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
    val total: Int,
    val accent: Color
) {
    WEEKLY("Thẻ Tuần", 100, 50, 7, 450, Cyan),
    MONTHLY("Thẻ Tháng", 500, 70, 30, 2600, Gold)
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

private enum class AppTab(val label: String, val symbol: String) {
    HOME("Tổng quan", "◆"), HISTORY("Lịch sử", "≡"), CARDS("Thẻ", "▣")
}

@Composable
private fun DiamondVaultApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { DiamondStore(context) }
    var snapshot by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Gold,
            background = Navy,
            surface = Panel,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Navy) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = { AppHeader() },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = Panel,
                        tonalElevation = 0.dp
                    ) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(item.symbol, fontSize = 20.sp, fontWeight = FontWeight.Black) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Navy,
                                    selectedTextColor = Cyan,
                                    indicatorColor = Cyan,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab != AppTab.CARDS) {
                        FloatingActionButton(
                            onClick = { showAdd = true },
                            containerColor = Cyan,
                            contentColor = Navy,
                            shape = CircleShape
                        ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 28.sp) }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Navy, Color(0xFF0B1625), Navy)))
                        .padding(padding)
                ) {
                    when (tab) {
                        AppTab.HOME -> Dashboard(
                            snapshot = snapshot,
                            onGoalClick = { showGoal = true },
                            onClaim = { snapshot = store.claim(snapshot, it, LocalDate.now()) },
                            onSeeCards = { tab = AppTab.CARDS }
                        )
                        AppTab.HISTORY -> History(
                            entries = snapshot.entries,
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
            .background(Navy.copy(alpha = .96f))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Cyan.copy(.25f), Gold.copy(.20f)))),
            contentAlignment = Alignment.Center
        ) { DiamondMark(26.dp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("KHO KIM CƯƠNG", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text("Gọn nhẹ • riêng tư • offline", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Dashboard(
    snapshot: AppSnapshot,
    onGoalClick: () -> Unit,
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
        contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BalanceCard(balance, received, spent) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Chuỗi ngày", "$streak ngày", "🔥", Gold, Modifier.weight(1f))
                MiniStat("Giao dịch", snapshot.entries.size.toString(), "✦", Cyan, Modifier.weight(1f))
            }
        }
        item { GoalCard(balance, snapshot.goal, onGoalClick) }
        item {
            SectionTitle("Nhận KC hôm nay", if (active.isEmpty()) "Xem thẻ" else null, onSeeCards)
        }
        if (active.isEmpty()) {
            item {
                EmptyCard(
                    title = "Chưa có thẻ hoạt động",
                    message = "Kích hoạt Thẻ Tuần hoặc Thẻ Tháng để không quên nhận KC mỗi ngày.",
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
            item { EmptyCard("Kho đang trống", "Chạm nút + để ghi lần nạp hoặc chi KC đầu tiên.") }
        } else {
            items(snapshot.entries.take(4), key = { it.id }) { EntryRow(it) }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int, received: Int, spent: Int) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF10283A), Color(0xFF102033), Color(0xFF211D30)),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Text("SỐ DƯ HIỆN TẠI", color = Muted, fontSize = 12.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiamondMark(35.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(formatNumber(balance), fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(7.dp))
                    Text("KC", color = Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = Color.White.copy(.08f))
                Spacer(Modifier.height(16.dp))
                Row {
                    BalanceStat("Đã nhận", "+${formatNumber(received)}", Mint, Modifier.weight(1f))
                    BalanceStat("Đã chi", "-${formatNumber(spent)}", Coral, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

@Composable
private fun MiniStat(label: String, value: String, symbol: String, accent: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Panel)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(.13f)), contentAlignment = Alignment.Center) {
                Text(symbol, color = accent, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, color = Muted, fontSize = 11.sp)
                Text(value, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GoalCard(balance: Int, goal: Int, onClick: () -> Unit) {
    val progress = DiamondMath.goalProgress(balance, goal)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(Panel)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Mục tiêu tiếp theo", fontWeight = FontWeight.Bold)
                    Text("${formatNumber(balance.coerceAtLeast(0))} / ${formatNumber(goal)} KC", color = Muted, fontSize = 13.sp)
                }
                Text("${(progress * 100).roundToInt()}%", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color = Gold,
                trackColor = Color.White.copy(.08f),
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(8.dp))
            Text("Chạm để đổi mục tiêu", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 19.sp)
        if (action != null) Text(action, color = Cyan, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun EmptyCard(title: String, message: String, action: String? = null, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Panel)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DiamondMark(32.dp, muted = true)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(message, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
            if (action != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun CompactClaimCard(membership: Membership, onClaim: (Long) -> Unit) {
    val today = LocalDate.now()
    val claimed = membership.hasClaimed(today)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Panel)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(membership.kind.accent.copy(.12f)), contentAlignment = Alignment.Center) {
                Text("+${membership.kind.daily}", color = membership.kind.accent, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(membership.kind.label, fontWeight = FontWeight.Bold)
                Text("${membership.claimedEpochDays.size}/${membership.kind.days} ngày đã nhận", color = Muted, fontSize = 12.sp)
            }
            Button(
                onClick = { onClaim(membership.id) },
                enabled = !claimed,
                colors = ButtonDefaults.buttonColors(containerColor = membership.kind.accent, contentColor = Navy),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text(if (claimed) "Đã nhận" else "Nhận", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun History(entries: List<DiamondEntry>, onRemove: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Lịch sử Kim Cương", fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text("Mọi dữ liệu chỉ nằm trên thiết bị này.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (entries.isEmpty()) item { EmptyCard("Chưa có giao dịch", "Các lần nạp, chi và nhận thẻ sẽ xuất hiện tại đây.") }
        items(entries, key = { it.id }) { entry -> EntryRow(entry, onRemove) }
    }
}

@Composable
private fun EntryRow(entry: DiamondEntry, onRemove: ((Long) -> Unit)? = null) {
    val positive = entry.amount >= 0
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Panel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background((if (positive) Mint else Coral).copy(.12f)), contentAlignment = Alignment.Center) {
                Text(if (positive) "+" else "−", color = if (positive) Mint else Coral, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(formatDate(entry.timestamp), entry.note.takeIf { it.isNotBlank() }).joinToString(" • "),
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                (if (positive) "+" else "") + formatNumber(entry.amount) + " KC",
                color = if (positive) Mint else Coral,
                fontWeight = FontWeight.Black
            )
            if (onRemove != null) {
                Spacer(Modifier.width(5.dp))
                Text("×", color = Muted, fontSize = 22.sp, modifier = Modifier.clickable { onRemove(entry.id) }.padding(5.dp))
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
        contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Thẻ ưu đãi", fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text("Theo dõi thủ công, không đăng nhập game và không tự nạp tiền.", color = Muted, fontSize = 13.sp)
        }
        items(MembershipKind.entries) { kind ->
            val current = active.firstOrNull { it.kind == kind }
            MembershipPlanCard(kind, current, onActivate, onClaim)
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(PanelSoft.copy(.75f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lưu ý", color = Gold, fontWeight = FontWeight.Bold)
                    Text(
                        "Mức 450/2.600 KC là cấu hình tham khảo phổ biến. Quyền lợi thực tế có thể được Garena thay đổi; hãy kiểm tra trong game trước khi mua.",
                        color = Muted,
                        fontSize = 12.sp,
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(Panel)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(kind.accent.copy(.16f), Color.Transparent)))
                .padding(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(kind.label.uppercase(), color = kind.accent, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Black)
                    Text("${formatNumber(kind.total)} KC", fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                DiamondMark(42.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Nhận ngay ${kind.instant} KC", fontWeight = FontWeight.Bold)
            Text("+ ${kind.daily} KC mỗi ngày × ${kind.days} ngày", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            if (membership == null) {
                Button(
                    onClick = { onActivate(kind) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = kind.accent, contentColor = Navy),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Kích hoạt theo dõi", fontWeight = FontWeight.Black) }
            } else {
                val claimed = membership.hasClaimed(today)
                val progress = membership.claimedEpochDays.size.toFloat() / kind.days
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = kind.accent,
                    trackColor = Color.White.copy(.08f)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Đã nhận ${membership.claimedEpochDays.size}/${kind.days} ngày", color = Muted, fontSize = 12.sp)
                    Button(
                        onClick = { onClaim(membership.id) },
                        enabled = !claimed,
                        colors = ButtonDefaults.buttonColors(containerColor = kind.accent, contentColor = Navy)
                    ) { Text(if (claimed) "Hôm nay ✓" else "+${kind.daily} Nhận", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onSave: (Int, String, String) -> Unit) {
    var isIncome by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = amount.toIntOrNull()?.takeIf { it > 0 }
    val presets = listOf(100, 310, 520, 1060, 2180)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (isIncome) "Ghi nhận KC" else "Ghi chi tiêu", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton("+ Nạp / nhận", isIncome, Mint, Modifier.weight(1f)) { isIncome = true }
                    ToggleButton("− Đã chi", !isIncome, Coral, Modifier.weight(1f)) { isIncome = false }
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
                colors = ButtonDefaults.buttonColors(containerColor = if (isIncome) Mint else Coral, contentColor = Navy)
            ) { Text("Lưu", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(accent, Navy) else ButtonDefaults.outlinedButtonColors(contentColor = Muted)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, colors = colors, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, colors = colors, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(label, fontSize = 12.sp) }
    }
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    val parsed = value.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Mục tiêu Kim Cương", fontWeight = FontWeight.Black) },
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
        confirmButton = { Button(onClick = { onSave(parsed!!) }, enabled = parsed != null) { Text("Cập nhật") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun DiamondMark(size: androidx.compose.ui.unit.Dp, muted: Boolean = false) {
    val primary = if (muted) Muted.copy(.45f) else Cyan
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
        drawPath(diamond, Brush.linearGradient(listOf(primary, if (muted) primary else Gold)))
        drawPath(diamond, Color.White.copy(.28f), style = Stroke(width = w * .045f))
        drawLine(Color.White.copy(.45f), Offset(w * .12f, h * .34f), Offset(w * .88f, h * .34f), w * .035f)
        drawLine(Color.White.copy(.3f), Offset(w * .32f, h * .12f), Offset(w * .50f, h * .90f), w * .03f)
        drawLine(Color.White.copy(.3f), Offset(w * .68f, h * .12f), Offset(w * .50f, h * .90f), w * .03f)
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
