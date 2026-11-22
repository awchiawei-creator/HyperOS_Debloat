package com.miui.debloater

data class DebloatItem(
    val pkg: String,
    val title: String,
    val description: String,
    val categoryIndex: Int,
    var checked: Boolean,
    var installStatus: String = ""
)

object DebloatRepository {
    // 核心防卡米/防变砖黑名单：绝不允许卸载或冻结
    val CRITICAL_PROTECTED = setOf(
        "com.miui.securitycenter",      // 手机管家（卸载必卡米）
        "com.xiaomi.finddevice",        // 查找设备（卸载必卡米）
        "com.miui.powerkeeper",         // 电量与性能
        "com.xiaomi.account",           // 小米账号
        "com.android.settings",         // 系统设置
        "com.android.systemui",         // 系统界面
        "com.android.updater",          // 系统更新
        "com.miui.home",                // 系统桌面
        "com.android.packageinstaller", // 原生安装器
        "com.miui.packageinstaller",    // 小米安装器
        "com.android.phone",            // 电话服务
        "com.android.providers.settings",
        "com.lbe.security.miui"         // 权限管理服务
    )

    fun getInitialItems(): MutableList<DebloatItem> = mutableListOf(
        // 分类 0: 广告与后台追踪（推荐全删）
        DebloatItem("com.miui.systemAdSolution", "小米系统广告组件 (msa)", "系统级广告核心源头，强烈建议精简", 0, true),
        DebloatItem("com.miui.analytics", "小米行为分析与广告统计", "后台收集使用数据并推送精准广告，强烈建议精简", 0, true),
        DebloatItem("com.miui.daemon", "系统后台日志与监控服务", "持续记录运行日志，精简可省电且无副作用", 0, true),
        DebloatItem("com.miui.hybrid", "快应用服务框架", "防止网页或广告自动跳转拉起免安装快应用", 0, true),
        DebloatItem("com.miui.hybrid.accessory", "快应用辅助组件", "快应用配套组件，建议一并精简", 0, true),
        DebloatItem("com.miui.bugreport", "用户反馈与错误报告", "系统崩溃日志上传工具，日常无需使用", 0, true),
        DebloatItem("com.miui.yellowpage", "生活黄页", "拨号与短信界面中的商业推广与服务号页面", 0, true),
        DebloatItem("com.miui.miservice", "服务与反馈", "小米售后服务与问题反馈入口", 0, true),
        DebloatItem("com.xiaomi.mirror", "MIUI+ / 跨屏协作服务", "若不使用小米电脑跨屏协同可精简", 0, false),
        DebloatItem("com.xiaomi.joyose", "云控与游戏温控配置", "含广告云控及游戏帧率温控限制（重度游戏玩家可保留）", 0, false),

        // 分类 1: 自带娱乐与可替代应用（按需勾选）
        DebloatItem("com.miui.player", "小米音乐", "内置大量开屏与信息流广告，改用第三方播放器可删", 1, false),
        DebloatItem("com.miui.video", "小米视频", "内置大量推送与广告，改用第三方播放器可删", 1, false),
        DebloatItem("com.android.browser", "小米浏览器", "建议安装 Chrome / Edge 后精简此项", 1, false),
        DebloatItem("com.xiaomi.gamecenter", "小米游戏中心", "游戏推广与分发中心，不玩小米渠道服可删", 1, false),
        DebloatItem("com.mipay.wallet", "小米钱包 / 金融", "不使用 NFC 公交卡、门禁卡、MiPay 可删", 1, false),
        DebloatItem("com.xiaomi.payment", "米币支付", "小米应用内购支付组件", 1, false),
        DebloatItem("com.miui.virtualsim", "全球上网", "出境虚拟流量卡服务，不用可删", 1, false),
        DebloatItem("com.miui.personalassistant", "负一屏（智能助理）", "桌面最左侧信息流与推荐卡片，删除后桌面更纯净", 1, false),
        DebloatItem("com.miui.voiceassist", "小爱同学", "不使用语音助手可精简", 1, false),
        DebloatItem("com.miui.contentextension", "传送门", "长按屏幕识图/分词功能，不用可精简", 1, false),
        DebloatItem("com.xiaomi.vipaccount", "小米社区", "米粉论坛客户端", 1, false),
        DebloatItem("com.miui.newmidrive", "小米云盘独立版", "独立网盘入口（不影响设置里的系统云备份）", 1, false),
        DebloatItem("com.sohu.inputmethod.sogou.xiaomi", "搜狗输入法小米版", "安装其他输入法后可精简（注意保留至少一个输入法）", 1, false),
        DebloatItem("com.baidu.input_mi", "百度输入法小米版", "安装其他输入法后可精简（注意保留至少一个输入法）", 1, false),

        // 分类 2: 系统级防广告加固（推荐勾选）
        DebloatItem("__SYS_LIMIT_AD_TRACKING__", "开启系统级限制广告追踪", "修改系统底层参数 limit_ad_tracking=1", 2, true),
        DebloatItem("__SYS_BLOCK_SHAKE_ADS__", "批量拦截第三方 App“摇一摇”权限", "通过 appops 批量拒绝所有第三方应用的设备动作传感器权限", 2, true)
    )
}
