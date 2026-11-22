package com.miui.debloater

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private val allItems = DebloatRepository.getInitialItems()
    private var currentCategory = 0
    private lateinit var adapter: DebloatAdapter

    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnShizukuAction: Button
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnRun: Button
    private lateinit var btnRestore: Button
    private lateinit var rbUninstall: RadioButton

    private var shellService: IShellService? = null
    private var isServiceBound = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, ShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell_service")
            .debuggable(false)
            .version(1)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShellService.Stub.asInterface(service)
            isServiceBound = true
            runOnUiThread {
                updateStatusUI()
                appendLog("🟢 Shizuku 底层执行服务已连接，随时可以开始精简！")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            isServiceBound = false
            runOnUiThread { updateStatusUI() }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkAndBindShizuku()
            updateStatusUI()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shellService = null
        isServiceBound = false
        runOnUiThread { updateStatusUI() }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
                checkAndBindShizuku()
            }
            updateStatusUI()
        }

    private val pickMarkdownLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importMarkdownFromUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        btnShizukuAction = findViewById(R.id.btnShizukuAction)
        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
        btnRun = findViewById(R.id.btnRun)
        btnRestore = findViewById(R.id.btnRestore)
        rbUninstall = findViewById(R.id.rbUninstall)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DebloatAdapter(emptyList()) { updateButtonCounts() }
        recyclerView.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText("1.广告追踪(推荐)"))
        tabLayout.addTab(tabLayout.newTab().setText("2.自带应用"))
        tabLayout.addTab(tabLayout.newTab().setText("3.防摇一摇"))
        tabLayout.addTab(tabLayout.newTab().setText("4.指南导入"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentCategory = tab.position
                refreshDisplayedList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        findViewById<Button>(R.id.btnHelp).setOnClickListener { showBeginnerHelpDialog() }
        findViewById<Button>(R.id.btnSelectRec).setOnClickListener { selectRecommended() }
        findViewById<Button>(R.id.btnDeselectAll).setOnClickListener { deselectAll() }
        findViewById<Button>(R.id.btnImportMd).setOnClickListener {
            pickMarkdownLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
        }
        findViewById<Button>(R.id.btnScanStatus).setOnClickListener { scanInstalledStatus() }

        btnShizukuAction.setOnClickListener { handleShizukuAction() }
        btnRun.setOnClickListener { confirmAndExecute(isClean = true) }
        btnRestore.setOnClickListener { confirmAndExecute(isClean = false) }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        refreshDisplayedList()
        updateButtonCounts()
        checkAndBindShizuku()
        updateStatusUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        if (isServiceBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            } catch (_: Exception) {
            }
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun checkAndBindShizuku() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (!isServiceBound) {
                try {
                    Shizuku.bindUserService(userServiceArgs, serviceConnection)
                } catch (e: Exception) {
                    appendLog("⚠️ 绑定 Shizuku 服务异常: ${e.message}")
                }
            }
        }
    }

    private fun updateStatusUI() {
        when {
            !isShizukuInstalled() -> {
                tvShizukuStatus.text = "🔴 未安装 Shizuku（无需电脑的权限引擎）"
                tvShizukuStatus.setTextColor(Color.parseColor("#D93025"))
                btnShizukuAction.text = "查看安装说明"
            }
            !Shizuku.pingBinder() -> {
                tvShizukuStatus.text = "🟠 Shizuku 未启动 —— 请先打开 Shizuku 并通过无线调试启动"
                tvShizukuStatus.setTextColor(Color.parseColor("#E37400"))
                btnShizukuAction.text = "打开 Shizuku"
            }
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> {
                tvShizukuStatus.text = "🟠 Shizuku 已运行，点击右侧按钮授权本应用"
                tvShizukuStatus.setTextColor(Color.parseColor("#E37400"))
                btnShizukuAction.text = "立即授权"
            }
            shellService == null -> {
                tvShizukuStatus.text = "🟡 正在连接 Shizuku 底层服务..."
                tvShizukuStatus.setTextColor(Color.parseColor("#E37400"))
                btnShizukuAction.text = "重新连接"
            }
            else -> {
                tvShizukuStatus.text = "🟢 Shizuku 已就绪 —— 可直接在手机上一键精简！"
                tvShizukuStatus.setTextColor(Color.parseColor("#188038"))
                btnShizukuAction.text = "已连接"
            }
        }
    }

    private fun handleShizukuAction() {
        when {
            !isShizukuInstalled() -> showBeginnerHelpDialog()
            !Shizuku.pingBinder() -> {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) startActivity(intent)
            }
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> {
                Shizuku.requestPermission(1001)
            }
            else -> {
                checkAndBindShizuku()
                updateStatusUI()
            }
        }
    }

    private fun refreshDisplayedList() {
        val filtered = allItems.filter { it.categoryIndex == currentCategory }
        adapter.updateList(filtered)
    }

    private fun updateButtonCounts() {
        val count = allItems.count { it.checked }
        btnRun.text = "🚀 一键精简 ($count)"
        btnRestore.text = "♻️ 一键恢复 ($count)"
    }

    private fun selectRecommended() {
        val defaults = DebloatRepository.getInitialItems().associate { it.pkg to it.checked }
        allItems.forEach { item ->
            item.checked = defaults[item.pkg] ?: false
        }
        adapter.notifyDataSetChanged()
        updateButtonCounts()
        appendLog("已恢复默认安全推荐勾选项。")
    }

    private fun deselectAll() {
        allItems.forEach { it.checked = false }
        adapter.notifyDataSetChanged()
        updateButtonCounts()
        appendLog("已取消所有勾选项。")
    }

    private fun importMarkdownFromUri(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: ""

            val regex = Regex("""\b(?:com|org|cn|net|miui|xiaomi)\.[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+\b""")
            val found = regex.findAll(content).map { it.value }.toSet()

            val blocked = found.filter { it in DebloatRepository.CRITICAL_PROTECTED }
            val valid = found.filter { it !in DebloatRepository.CRITICAL_PROTECTED }.sorted()

            if (valid.isEmpty()) {
                Toast.makeText(this, "文件中未提取到有效的应用包名", Toast.LENGTH_SHORT).show()
                return
            }

            var matched = 0
            var added = 0
            val existingMap = allItems.associateBy { it.pkg }

            for (pkg in valid) {
                val exist = existingMap[pkg]
                if (exist != null) {
                    exist.checked = true
                    matched++
                } else {
                    allItems.add(
                        DebloatItem(
                            pkg = pkg,
                            title = "指南提取组件",
                            description = "从外部 .md 指南自动提取（建议按需勾选）",
                            categoryIndex = 3,
                            checked = true
                        )
                    )
                    added++
                }
            }

            findViewById<TabLayout>(R.id.tabLayout).getTabAt(3)?.select()
            refreshDisplayedList()
            updateButtonCounts()

            appendLog("📄 已从指南提取 ${valid.size} 个包名（同步勾选内置 $matched 个，新增 $added 个）。")
            if (blocked.isNotEmpty()) {
                appendLog("🛡️ 防卡米保护已自动屏蔽高危系统包：${blocked.joinToString(", ")}")
            }
        } catch (e: Exception) {
            appendLog("❌ 导入文件失败: ${e.message}")
        }
    }

    private fun scanInstalledStatus() {
        val service = shellService
        if (service == null) {
            Toast.makeText(this, "请先启动并授权 Shizuku！", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            appendLog("🔍 正在扫描当前手机组件安装状态...")
            val (allSet, disabledSet) = withContext(Dispatchers.IO) {
                val allOut = service.exec("pm list packages")
                val disOut = service.exec("pm list packages -d")
                val a = allOut.lines().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toSet()
                val d = disOut.lines().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toSet()
                Pair(a, d)
            }

            allItems.forEach { item ->
                if (!item.pkg.startsWith("__SYS_")) {
                    item.installStatus = when {
                        item.pkg !in allSet -> "【当前状态：已精简/未安装】"
                        item.pkg in disabledSet -> "【当前状态：已冻结停用】"
                        else -> "【当前状态：正在运行】"
                    }
                }
            }
            adapter.notifyDataSetChanged()
            appendLog("✅ 状态扫描完成！可在列表中查看每项状态。")
        }
    }

    private fun confirmAndExecute(isClean: Boolean) {
        val service = shellService
        if (service == null) {
            Toast.makeText(this, "Shizuku 未就绪，请先点击顶部右侧按钮完成激活与授权！", Toast.LENGTH_LONG).show()
            return
        }

        val selected = allItems.filter { it.checked }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先勾选至少一个项目！", Toast.LENGTH_SHORT).show()
            return
        }

        val actionText = if (isClean) "精简/关闭" else "恢复"
        AlertDialog.Builder(this)
            .setTitle("确认批量$actionText")
            .setMessage("确定要对已勾选的 ${selected.size} 个项目执行【$actionText】操作吗？")
            .setPositiveButton("确定执行") { _, _ ->
                executeBatch(service, selected, isClean)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBatch(service: IShellService, selected: List<DebloatItem>, isClean: Boolean) {
        val useUninstall = rbUninstall.isChecked
        val actionText = if (isClean) "精简" else "恢复"
        btnRun.isEnabled = false
        btnRestore.isEnabled = false

        lifecycleScope.launch {
            appendLog("\n========== 开始执行批量$actionText（共 ${selected.size} 项） ==========")

            withContext(Dispatchers.IO) {
                for (item in selected) {
                    if (item.pkg in DebloatRepository.CRITICAL_PROTECTED) {
                        withContext(Dispatchers.Main) {
                            appendLog("🛡️ [防变砖跳过] ${item.title} (${item.pkg})")
                        }
                        continue
                    }

                    when (item.pkg) {
                        "__SYS_LIMIT_AD_TRACKING__" -> {
                            val v = if (isClean) "1" else "0"
                            service.exec("settings put secure limit_ad_tracking $v")
                            withContext(Dispatchers.Main) {
                                appendLog("✅ [${if (isClean) "已开启" else "已还原"}] 系统级限制广告追踪")
                            }
                        }
                        "__SYS_BLOCK_SHAKE_ADS__" -> {
                            val mode = if (isClean) "ignore" else "allow"
                            val pkgsOut = service.exec("pm list packages -3")
                            var cnt = 0
                            for (line in pkgsOut.lines()) {
                                val p = line.removePrefix("package:").trim()
                                if (p.isNotEmpty()) {
                                    service.exec("appops set $p 10017 $mode")
                                    cnt++
                                }
                            }
                            withContext(Dispatchers.Main) {
                                appendLog("✅ [${if (isClean) "已禁用" else "已恢复"}] $cnt 个第三方应用的“摇一摇”权限")
                            }
                        }
                        else -> {
                            if (isClean) {
                                val cmd = if (useUninstall) {
                                    "pm uninstall -k --user 0 ${item.pkg}"
                                } else {
                                    "pm disable-user --user 0 ${item.pkg}"
                                }
                                val res = service.exec(cmd)
                                withContext(Dispatchers.Main) {
                                    when {
                                        res.contains("Success") || res.contains("disabled") ->
                                            appendLog("✅ [精简成功] ${item.title} (${item.pkg})")
                                        res.contains("not installed for 0") ->
                                            appendLog("⚪ [此前已精简] ${item.title} (${item.pkg})")
                                        else ->
                                            appendLog("⚠️ [跳过] ${item.title} -> $res")
                                    }
                                }
                            } else {
                                service.exec("pm enable ${item.pkg}")
                                val res = service.exec("cmd package install-existing ${item.pkg}")
                                withContext(Dispatchers.Main) {
                                    if (res.contains("installed") || res.contains("enabled")) {
                                        appendLog("♻️ [恢复成功] ${item.title} (${item.pkg})")
                                    } else {
                                        appendLog("ℹ️ [无需恢复] ${item.title} -> $res")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            appendLog("========== 全部执行完毕！建议重启一次手机使更改彻底生效 ==========")
            btnRun.isEnabled = true
            btnRestore.isEnabled = true
            AlertDialog.Builder(this@MainActivity)
                .setTitle("操作完成")
                .setMessage("批量${actionText}已完成！建议重启一次手机。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun appendLog(msg: String) {
        tvLog.append(msg + "\n")
        scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun showBeginnerHelpDialog() {
        val msg = """
            【全程无需电脑！在小米手机上 3 步激活】

            第 1 步：安装 Shizuku（开源免费权限工具）
            在浏览器搜索下载并安装「Shizuku」App（GitHub 或酷安均可下载）。

            第 2 步：在手机上无线激活 Shizuku
            1. 打开手机【设置】→【我的设备】→【全部参数与信息】，连续点击【OS版本】7次开启开发者模式。
            2. 返回【设置】→【更多设置】→【开发者选项】，开启【USB调试】、【无线调试】以及【USB调试(安全设置)】。
            3. 打开 Shizuku App，点击【配对】→ 点开通知栏输入【无线调试】里的 6 位配对码 → 返回 Shizuku 点击【启动】。

            第 3 步：回到本软件一键去广告
            看到顶部状态栏变成绿色「Shizuku 已就绪」后，勾选想清理的广告组件（或导入 GitHub 指南 .md 文件），点击底部【一键精简】即可！
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("手机版小白使用教程")
            .setMessage(msg)
            .setPositiveButton("我知道了", null)
            .show()
    }
}
