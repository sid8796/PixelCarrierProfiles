package com.example.simregionswitcher

import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

/** A1-only profile UI with dual-SIM diagnostics and historical request recovery. */
class RegionActivity : Activity() {
    private lateinit var palette: PixelPalette
    private val pageColor get() = palette.page
    private val ink get() = palette.ink
    private val secondary get() = palette.secondary
    private val blue get() = palette.primary
    private val outline get() = palette.outline
    private val surface get() = palette.surface
    private val tonal get() = palette.tonal
    private val imsRows = mutableMapOf<Int, List<TextView>>()
    private val imsNumbers = mutableMapOf<Int, TextView>()
    private val imsNumberNotes = mutableMapOf<Int, TextView>()
    private var pendingNumberTarget: ImsNumberSession.Target? = null
    private var pendingCapabilityId: Long? = null
    private val cardContainers = mutableMapOf<Int, LinearLayout>()
    private var lastApnEvent = ""
    private lateinit var apnCode: TextView
    private var imsSlot = -1
    private var imsTarget: ImsNumberSession.Target? = null
    private val imsHeaders = mutableMapOf<Int, TextView>()
    private val imsPanels = mutableMapOf<Int, LinearLayout>()
    private val imsBodies = mutableMapOf<Int, LinearLayout>()
    private val imsTexts = mutableMapOf<Int, TextView>()
    private val imsActions = mutableMapOf<Int, List<Button>>()
    private var selectedSlot = 0
    private var editingSub = -1
    private var visible = false
    private var subscriptions: List<SubscriptionInfo> = emptyList()
    private var subscriptionError = false
    private var lastRegionEvent = ""
    private var apiSince = Long.MAX_VALUE
    private var apiTarget: SimRecord? = null
    private val readings = mutableMapOf<Int, RadioSummary.Reading>()
    private val handler = Handler(Looper.getMainLooper())
    private val logLines = ArrayDeque<String>()
    private var selectedPage=0
    private val pageScroll=IntArray(4)
    private val pages=mutableListOf<LinearLayout>()
    private val navigationItems=mutableListOf<Button>()
    private val pageSimButtons=mutableListOf<Button>()
    private lateinit var pageScrollView: ScrollView
    private var journalDetailsText = ""
    private lateinit var journalDetailsButton: Button
    private lateinit var journalMetadata: TextView
    private lateinit var journalTitle: TextView
    private lateinit var journalSteps: LinearLayout
    private lateinit var journalSummary: TextView
    private lateinit var settingsAppearance: Button
    private lateinit var settingsLanguage: Button
    private lateinit var settingsColors: Button
    private lateinit var phoneSetting: Button
    private lateinit var shizukuSetting: Button
    private var journalRenderKey=""
    private lateinit var settingsStatus: TextView
    private lateinit var settingsMain: LinearLayout
    private lateinit var settingsApnPage: LinearLayout
    private lateinit var settingsApnLink: Button
    private var settingsApnOpen = false
    private var settingsPbOpen=false
    private lateinit var settingsPbPage: LinearLayout
    private lateinit var pbAcknowledge: Button
    private lateinit var pbApply: Button
    private lateinit var pbClear: Button
    private lateinit var pbRead: Button
    private lateinit var pbStatus: TextView
    private lateinit var pbReadback: TextView
    private var pbReadTarget: RegionPlan?=null
    private var profileValues:Map<String,Any?>?=null
    private var profileValuesAt=0L
    private var profileReadError:String?=null
    private var profileAutoPending=false
    private var profileAutoQueued=false
    private val profileAutoRead=Runnable {
        profileAutoQueued=false
        if(profileAutoPending && canAutoCheckProfile()) readLocalPb()
    }
    private lateinit var homeProfileCard:LinearLayout
    private lateinit var homeProfileTitle:TextView
    private lateinit var homeProfileSubtitle:TextView
    private lateinit var homeProfileBadge:TextView
    private lateinit var homeProfileOpen:Button
    private lateinit var carrierRefusalAck:Button
    private val profileApplyButtons=linkedMapOf<String,Button>()
    private val profileMatchLabels=linkedMapOf<String,TextView>()
    private val profileCardViews=linkedMapOf<String,LinearLayout>()
    private lateinit var profileList:LinearLayout
    private lateinit var profileEmpty:TextView
    private val profileActionRows=linkedMapOf<String,LinearLayout>()
    private val profileCheckButtons=linkedMapOf<String,Button>()
    private var profileOrderKey=""
    private lateinit var pbDefinitions:TextView
    private lateinit var localApply:Button
    private lateinit var copyLogButton: Button
    private lateinit var root: LinearLayout
    private lateinit var shizukuBanner: TextView
    private lateinit var phoneBanner: Button
    private lateinit var original: TextView
    private lateinit var inspect: Button
    private lateinit var archiveV1: Button
    private lateinit var outcome: LinearLayout
    private lateinit var resultTarget: TextView
    private lateinit var resultSummary: TextView
    private lateinit var resultMore: Button
    private var resultExpanded=false
    private lateinit var result: TextView
    private lateinit var history: TextView
    private lateinit var apiButton: Button
    private lateinit var apiResult: TextView
    private lateinit var tools: LinearLayout
    private lateinit var toolsButton: Button
    private lateinit var logView: TextView
    private lateinit var logButton: Button
    private lateinit var apnToggle: Switch
    private lateinit var apnToggleHint: TextView
    private lateinit var apnSummary: TextView
    private lateinit var apnExtra: LinearLayout
    private lateinit var apnExtraButton: Button
    private var renderingApnToggle=false
    private lateinit var apnTitle: TextView
    private lateinit var apnInfo: TextView
    private lateinit var apnResult: TextView
    private lateinit var apnRead: Button
    private lateinit var apnSave: Button
    private lateinit var apnRestore: Button
    private lateinit var apnForget: Button
    private lateinit var radioTime: TextView
    private val cardName = mutableMapOf<Int, TextView>()
    private val cardNetwork = mutableMapOf<Int, TextView>()
    private val cardLabels = mutableMapOf<Int, TextView>()
    private val cardCarrierId = mutableMapOf<Int, TextView>()
    private val cardSignal = mutableMapOf<Int, TextView>()
    private val bars = mutableMapOf<Int, List<View>>()
    private val listener: () -> Unit = { if (::localApply.isInitialized && !isDestroyed) render() }
    private var permissionPromptPending=false
    private val startupAccess = Runnable { maybeRequestStartupAccess() }
    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { if (!isDestroyed) { render(); scheduleStartupAccess() } } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { if (!isDestroyed) render() } }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { request, _ ->
        if (request == 820) runOnUiThread { if (!isDestroyed) { permissionPromptPending=false;log("SHIZUKU_PERMISSION_RESULT"); render();scheduleStartupAccess() } }
    }
    private val radioTick = object : Runnable {
        override fun run() { if (visible) { refreshRadio(); renderCards(); handler.postDelayed(this, 5_000) } }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(fill: Int, border: Int = Color.TRANSPARENT, radius: Int = 24) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); if (border != Color.TRANSPARENT) setStroke(dp(1), border)
    }
    private fun params(bottom: Int = 10) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }
    private fun text(parent: LinearLayout, label: String = "", size: Float = 14f, bold: Boolean = false, color: Int = ink): TextView =
        TextView(this).apply {
            text = label; textSize = size; setTextColor(color); includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1.05f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            parent.addView(this, if (parent.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f) else params(7))
        }
    private fun panel(parent: LinearLayout = root): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(surface)
        parent.addView(this, params(12))
    }
    private fun tintButton(button: Button, filled: Boolean) {
        button.backgroundTintList = null
        val disabled = palette.outline
        button.background = RippleDrawable(ColorStateList.valueOf((ink and 0x00FFFFFF) or 0x22000000),
            shape(if (filled) blue else tonal, radius = 28), null)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabled, if (filled) palette.onPrimary else ink)))
        button.elevation = 0f; button.stateListAnimator = null
    }
    private fun textButton(button: Button) {
        button.background = RippleDrawable(ColorStateList.valueOf((ink and 0x00FFFFFF) or 0x22000000),
            shape(Color.TRANSPARENT), null)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(outline, blue)))
    }
    private fun animateDisclosure() {
        if (ValueAnimator.areAnimatorsEnabled()) TransitionManager.beginDelayedTransition(root, AutoTransition().apply { duration = 180 })
    }
    private fun button(parent: LinearLayout, label: Int, filled: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        setText(label); isAllCaps = false; textSize = 14f; minimumHeight = dp(48); minHeight = dp(48)
        setPadding(dp(10), dp(9), dp(10), dp(9)); tintButton(this, filled)
        parent.addView(this, params(8)); setOnClickListener { action() }
    }
    private fun settingsRow(parent: LinearLayout, label: Int, dropdown: Boolean = false, action: () -> Unit): Button = button(parent,label,action=action).apply {
        gravity=Gravity.START or Gravity.CENTER_VERTICAL
        textSize=14f; minimumHeight=dp(56);minHeight=dp(56)
        setPadding(dp(10),dp(8),dp(28),dp(8))
        background=android.graphics.drawable.LayerDrawable(arrayOf(shape(Color.TRANSPARENT),PixelChevronDrawable(secondary,resources.displayMetrics.density,!dropdown)))
        setTextColor(ink)
        (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(2)
    }
    private fun styleSettingsValue(view: TextView) {
        val value=android.text.SpannableString(view.text)
        val start=value.indexOf('\n')+1
        if(start>0 && start<value.length) {
            value.setSpan(android.text.style.RelativeSizeSpan(0.85f),start,value.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            value.setSpan(android.text.style.ForegroundColorSpan(secondary),start,value.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.text=value
    }
    private fun chooseLanguage() {
        val manager=getSystemService(android.app.LocaleManager::class.java)
        val current=manager.applicationLocales
        val selected=when { current.isEmpty -> 0;current[0].language=="ru" -> 1;else -> 2 }
        AlertDialog.Builder(this).setTitle(R.string.language_title)
            .setSingleChoiceItems(arrayOf(getString(R.string.polish_system),"Русский","English"),selected) { dialog,index ->
                saveDraft();dialog.dismiss()
                manager.applicationLocales=android.os.LocaleList.forLanguageTags(when(index) { 1 -> "ru";2 -> "en";else -> "" })
            }.setNegativeButton(android.R.string.cancel,null).show()
    }
    private fun showApnSettings(open: Boolean) {
        if(!::settingsMain.isInitialized) return
        settingsApnOpen=open
        settingsPbOpen=false
        if(::settingsPbPage.isInitialized) settingsPbPage.visibility=View.GONE
        settingsMain.visibility=if(open) View.GONE else View.VISIBLE
        settingsApnPage.visibility=if(open) View.VISIBLE else View.GONE
        pageScrollView.post { pageScrollView.scrollTo(0,0) }
    }
    private fun showLocalPb() {
        settingsApnOpen=false;settingsPbOpen=true
        settingsMain.visibility=View.GONE;settingsApnPage.visibility=View.GONE;settingsPbPage.visibility=View.VISIBLE
        pageScrollView.post { pageScrollView.scrollTo(0,0) }
        renderLocalPb()
    }
    private fun persistenceRequested() = false
    private fun localPbPlan(clear: Boolean=false): RegionPlan? {
        val sub=selected() ?: return null
        val profile=BundledProfiles.find(sub.mccString,sub.mncString) ?: return null
        return RegionPlan(sub.simSlotIndex,sub.subscriptionId,sub.displayName.toString().take(100),sub.mccString ?: "",sub.mncString ?: "",sub.isEmbedded,
            sub.mccString ?: "",sub.mncString ?: "",configMode=if(clear) profile.clearMode else profile.applyMode,persistConfig=!clear && persistenceRequested()).takeIf { it.valid() }
    }
    private fun confirmLocalPb(clear: Boolean) {
        val plan=localPbPlan(clear) ?: return
        if(!RegionSession.canApplyDirect(plan) || ReaderSession.accessError()!=null) return
        val title=if(clear) R.string.pb_clear else R.string.pb_apply
        val body=getString(if(clear) R.string.pb_clear_confirm else if(plan.configMode==T2RuRules.APPLY) R.string.profiles_t2_confirm else if(plan.configMode==MegafonRuRules.APPLY) R.string.profiles_megafon_confirm else if(plan.configMode==BeelineRuRules.APPLY) R.string.profiles_beeline_confirm else if(plan.configMode==MtsRuRules.APPLY) R.string.profiles_mts_ru_confirm else if(plan.configMode==MtsByRules.APPLY) R.string.profiles_mts_confirm else R.string.pb_apply_confirm,plan.slot+1,plan.operator,plan.subId)+
            if(clear) "" else "\n\n"+getString(if(plan.persistConfig) R.string.persist_confirm_on else R.string.persist_confirm_off)
        AlertDialog.Builder(this).setTitle(title).setMessage(body)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(title) { _,_ ->
                if(localPbPlan(clear)==plan && RegionSession.canApplyDirect(plan) && ReaderSession.accessError()==null) {
                    pbReadTarget=null;profileValues=null;profileReadError=null;pbReadback.setText(R.string.pb_not_read)
                    OperationJournal.begin(plan,false)
                    log(if(clear) "LOCAL_PB_CLEAR_CONFIRMED_SIM_${plan.slot+1}" else "LOCAL_PB_APPLY_CONFIRMED_SIM_${plan.slot+1}")
                    profileAutoPending=true
                    RegionSession.applyDirect(this,plan,restoreApn=false)
                }
            }.show()
    }
    private fun readLocalPb() {
        val plan=localPbPlan() ?: return
        if(RegionSession.busy || RegionSession.blocksReads || ReaderSession.state.running || ApnSession.pending) return
        profileAutoPending=false
        val id=requireNotNull(BundledProfiles.forMode(plan.configMode)).id
        val result=LocalPbProfile.read(this,plan.subId,id)
        if(localPbPlan()!=plan) return
        pbReadTarget=plan
        profileValues=result.values;profileReadError=result.error;profileValuesAt=System.currentTimeMillis()
        val state=ProfileOverviewRules.checkStateFor(result.values,id,result.error)
        log("PROFILE_CHECK:SIM_${plan.slot+1}:state=${state.name}:known=${result.values.count { it.value!=null }}:missing=${result.values.count { it.value==null }}:error=${result.error ?: "none"}")
        val rows=result.values.map { (key,value) -> key+": "+(value?.toString() ?: getString(R.string.check_missing_value)) }.joinToString("\n")
        pbReadback.text=getString(R.string.pb_read_result,plan.slot+1,DateFormat.getTimeInstance().format(Date()),(result.error?.let { "$it\n" } ?: "")+rows)
        renderProfileOverview()
        renderHomeProfileOffer()
    }
    private fun profileCheckState(plan:RegionPlan?):ProfileOverviewRules.CheckState {
        if(plan==null || pbReadTarget!=plan || RegionSession.busy || profileValuesAt<(RegionSession.history(plan.slot)?.time ?: 0L))
            return ProfileOverviewRules.CheckState.NOT_CHECKED
        val id=BundledProfiles.forMode(plan.configMode)?.id
        return ProfileOverviewRules.checkStateFor(profileValues,requireNotNull(id),profileReadError)
    }
    private fun checkStateLabel(state:ProfileOverviewRules.CheckState)=when(state) {
        ProfileOverviewRules.CheckState.NOT_CHECKED -> R.string.overview_not_checked
        ProfileOverviewRules.CheckState.ACTIVE -> R.string.check_state_active
        ProfileOverviewRules.CheckState.DIFFERENT -> R.string.check_state_different
        ProfileOverviewRules.CheckState.FAILED -> R.string.check_state_failed
    }
    private fun canAutoCheckProfile()=visible && selectedPage in 0..1 && localPbPlan()!=null &&
        !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running && !ApnSession.pending
    private fun requestProfileCheck() { profileAutoPending=true;maybeScheduleProfileCheck() }
    private fun maybeScheduleProfileCheck() {
        if(!::localApply.isInitialized || !profileAutoPending || profileAutoQueued || !canAutoCheckProfile()) return
        profileAutoQueued=true;handler.postDelayed(profileAutoRead,350)
    }
    private fun renderLocalPb() {
        if(!::pbApply.isInitialized) return
        val plan=localPbPlan()
        val selectedProfile=BundledProfiles.forMode(plan?.configMode)
        pbDefinitions.text=selectedProfile?.let { profileDefinitions(it.id) } ?: getString(R.string.profiles_unsupported)
        val phone=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
        val ready=plan!=null && phone && ReaderSession.accessError()==null && RegionSession.canApplyDirect(plan)
        pbApply.isEnabled=ready;pbClear.isEnabled=ready
        pbAcknowledge.visibility=if(RegionSession.canAcknowledgeLocalApnFailure()) View.VISIBLE else View.GONE
        pbRead.isEnabled=plan!=null && phone && !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running && !ApnSession.pending
        pbStatus.text=when {
            plan==null -> getString(R.string.pb_a1_only)
            !phone || ReaderSession.accessError()!=null -> getString(R.string.profile_need_access)
            RegionSession.activePlan?.configOnly==true -> getString(RegionSession.messageResource())+"\n"+RegionSession.detail
            else -> getString(R.string.pb_restore_first)
        }
        if(pbReadTarget!=null && pbReadTarget!=plan) { pbReadTarget=null;profileValues=null;profileReadError=null;profileAutoPending=true;pbReadback.setText(R.string.pb_not_read) }
    }
    private fun rowButton(parent: LinearLayout, label: Int, action: () -> Unit): Button {
        val value = button(parent, label, action = action)
        value.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5); marginStart = dp(5) }
        return value
    }
    private fun simSymbol(parent: LinearLayout, size: Int = 25) {
        parent.addView(TextView(this).apply {
            text = "▦"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(palette.onPrimary)
            background = shape(blue, radius = 50); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginEnd = dp(8) })
    }
    private fun selected() = subscriptions.singleOrNull { it.simSlotIndex == selectedSlot }
    private fun drafts() = getSharedPreferences("region_profiles", MODE_PRIVATE)
    private fun saveDraft() { drafts().edit().putInt("selected_slot",selectedSlot).apply() }
    private fun chooseSlot(slot: Int) {
        if(selectedSlot != slot) closeIms()
        saveDraft(); selectedSlot = slot; drafts().edit().putInt("selected_slot", slot).apply()
        loadDraft(); render();requestProfileCheck()
        if(selectedPage==1) pageScrollView.post { pageScrollView.scrollTo(0,0) }
    }
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppearanceContext.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionPromptPending=savedInstanceState?.getBoolean("permissionPromptPending") ?: false
        OperationJournal.initialize(this)
        val dynamic = getSharedPreferences("pixel_appearance", MODE_PRIVATE).getBoolean("dynamic", true)
        if (!dynamic) theme.applyStyle(R.style.RegionFixedAccent, true)
        palette = PixelPalette.from(this, dynamic)
        ReaderSession.initialize(this); RegionSession.initialize(this); ApnSession.initialize(this)
        resultExpanded=savedInstanceState?.getBoolean("resultExpanded") ?: false
        selectedSlot = (savedInstanceState?.getInt("slot") ?: drafts().getInt("selected_slot", 0)).coerceIn(0, 1)
        getSharedPreferences("classic_ui_log", MODE_PRIVATE).getString("lines", "")?.lineSequence()?.filter { it.isNotBlank() }?.toList()?.takeLast(60)?.forEach { logLines.addLast(it) }
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.setSystemBarsAppearance(if (palette.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageColor) }
        outer.setOnApplyWindowInsetsListener { view, insets ->
            val system = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(system.left, system.top, system.right, maxOf(system.bottom, ime.bottom)); insets
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(18)) }
        val scroll = ScrollView(this).apply { addView(root); isFillViewport = true }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(outer); outer.requestApplyInsets()
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(heading, params(3))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        text(title, getString(R.string.region_title), 21f, true).apply { setAccessibilityHeading(true); layoutParams = params(3) }
        val version = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "—"
        text(title, getString(R.string.ui_version, version.substringBefore("-")), 11f, color = secondary)
        val manufacturer = Build.MANUFACTURER
        val device = if (Build.MODEL.startsWith(manufacturer, true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
        text(root, getString(R.string.classic_device, device, Build.VERSION.RELEASE, Build.DISPLAY.ifBlank { Build.ID }), 11f, false, secondary)
        shizukuBanner = text(root, "", 13f).apply { setPadding(dp(14), dp(5), dp(14), dp(5)); minimumHeight = dp(34); isClickable = true; isFocusable = true; setOnClickListener { authorizeShizuku() } }
        phoneBanner = button(root, R.string.region_phone_access) { requestPhone() }
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; root.addView(cards, params(8))
        for (slot in 0..1) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(9), dp(10), dp(9), dp(10)); minimumHeight = dp(116)
                background = shape(surface); isClickable = true; isFocusable = true; setOnClickListener { chooseSlot(slot) }
                contentDescription = getString(R.string.classic_slot, slot + 1)
            }
            cardContainers[slot] = card
            cards.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (slot == 0) marginEnd = dp(5) else marginStart = dp(5) })
            val label = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            card.addView(label, params(7)); simSymbol(label, 23); cardLabels[slot] = text(label, getString(R.string.classic_slot, slot + 1), 14f, color = secondary)
            cardName[slot] = text(card, "", 16f, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
            cardCarrierId[slot] = text(card, "", 10f, color = secondary).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }
            card.addView(View(this).apply { setBackgroundColor(outline) }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(7) })
            val signalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; card.addView(signalRow)
            val signalBars = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
            signalRow.addView(signalBars, LinearLayout.LayoutParams(dp(29), dp(27)).apply { marginEnd = dp(5) })
            bars[slot] = (0..3).map { i -> View(this).also { signalBars.addView(it, LinearLayout.LayoutParams(dp(4), dp(8 + i * 5)).apply { marginEnd = dp(2) }) } }
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; signalRow.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            cardNetwork[slot] = text(detail, "", 12f)
            cardSignal[slot] = text(detail, "", 11f, color = secondary)
        }
        buildImsSpoilers()
        val formStart=root.childCount
        val form = panel()
        original = text(form,"",12f,color=secondary)
        history = text(form,"",12f,color=secondary)
        inspect = button(form,R.string.trial_inspect) { RegionSession.inspect(this) }
        archiveV1 = button(form,R.string.native_v1_archive) {
            AlertDialog.Builder(this).setTitle(R.string.native_v1_archive).setMessage(R.string.native_v1_archive_confirm)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok) { _,_ -> RegionSession.archiveV1AfterReboot();render() }.show()
        }
        outcome = panel(form).apply { background=shape(tonal,radius=16) }
        text(outcome,getString(R.string.design_last_result),15f,true)
        resultSummary=text(outcome,"",13f)
        resultMore=button(outcome,R.string.design_details) { resultExpanded=!resultExpanded;render() }.also { textButton(it) }
        resultTarget = text(outcome, "", 12f, true); result = text(outcome, "", 12f)
        form.removeView(history);form.addView(history,params(0))
        buildApn()
        val testingStart=root.childCount
        buildTesting()
        Shizuku.addBinderReceivedListenerSticky(binderReceived); Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult); ReaderSession.listen(listener)
        tools.visibility = if (savedInstanceState?.getBoolean("tools") == true) View.VISIBLE else View.GONE
        logView.visibility = if (savedInstanceState?.getBoolean("log") == true) View.VISIBLE else View.GONE
        buildNavigation(outer,scroll,formStart,testingStart,savedInstanceState)
    }
    /** UI-only routing: the same form and sessions remain alive across tabs. */
    private fun buildNavigation(outer: LinearLayout, scroll: ScrollView, formStart: Int, testingStart: Int, saved: Bundle?) {
        pageScrollView=scroll
        val children=(0 until root.childCount).map { root.getChildAt(it) }
        root.removeAllViews()
        repeat(4) { pages.add(LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }); root.addView(pages.last(),params(0)) }
        children.forEachIndexed { index,view -> pages[if(index<formStart) 0 else if(index<testingStart) 1 else 2].addView(view) }
        fun move(view: View, parent: LinearLayout, at: Int = -1) {
            (view.parent as? ViewGroup)?.removeView(view)
            if(at<0) parent.addView(view) else parent.addView(view,at)
        }
        fun title(parent: LinearLayout,label: Int) { text(parent,getString(label),24f,true).setAccessibilityHeading(true) }
        fun simPicker(parent: LinearLayout): Button = button(parent,R.string.nav_choose_sim) {
            val labels=(0..1).map { slot -> getString(R.string.nav_selected_sim,slot+1,
                subscriptions.singleOrNull { it.simSlotIndex==slot }?.displayName ?: getString(R.string.classic_no_subscription)) }.toTypedArray()
            AlertDialog.Builder(this).setTitle(R.string.nav_choose_sim).setSingleChoiceItems(labels,selectedSlot) { dialog,which ->
                chooseSlot(which); dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel,null).show()
        }.also { pageSimButtons.add(it) }
        buildHomeProfileOffer()
        // Existing extra actions belong to Settings; tests and logs stay on Journal.
        val oldSection=children[testingStart]
        (oldSection.parent as? ViewGroup)?.removeView(oldSection)
        val aboutButton=children[testingStart+1]
        (aboutButton.parent as? ViewGroup)?.removeView(aboutButton)
        title(pages[3],R.string.nav_settings)
        settingsStatus=text(pages[3],"",13f,color=secondary).apply { visibility=View.GONE }
        val appearanceBox=panel(pages[3])
        text(appearanceBox,getString(R.string.polish_appearance),16f,true)
        settingsAppearance=settingsRow(appearanceBox,R.string.polish_theme,dropdown=true) { chooseTheme() }
        settingsColors=settingsRow(appearanceBox,R.string.pixel_colors,dropdown=true) { chooseColors() }
        settingsLanguage=settingsRow(appearanceBox,R.string.language_title,dropdown=true) { chooseLanguage() }
        val permissionBox=panel(pages[3])
        text(permissionBox,getString(R.string.polish_permissions),16f,true)
        phoneSetting=settingsRow(permissionBox,R.string.region_phone_access) {
            if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
            else requestPhone()
        }
        shizukuSetting=settingsRow(permissionBox,R.string.authorize_shizuku) { authorizeShizuku() }
        val apnLinkBox=panel(pages[3])
        settingsApnLink=settingsRow(apnLinkBox,R.string.settings_working_apn) { showApnSettings(true) }
        val helpBox=panel(pages[3])
        text(helpBox,getString(R.string.settings_help),16f,true)
        settingsRow(helpBox,R.string.pb_settings_title) { showLocalPb() }
        settingsRow(helpBox,R.string.pixel_about_device) { showAbout() }
        settingsMain=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val settingChildren=(0 until pages[3].childCount).map { pages[3].getChildAt(it) }
        settingChildren.forEach { move(it,settingsMain) };pages[3].addView(settingsMain)
        settingsApnPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        pages[3].addView(settingsApnPage)
        button(settingsApnPage,R.string.settings_back) { showApnSettings(false) }
        title(settingsApnPage,R.string.settings_working_apn)
        text(settingsApnPage,getString(R.string.settings_sim_apn),12f,color=secondary)
        simPicker(settingsApnPage)
        val apnSettings=panel(settingsApnPage)
        text(apnSettings,getString(R.string.compact_apn_details),12f,color=secondary)
        move(apnExtra,apnSettings);apnExtra.visibility=View.VISIBLE
        settingsPbPage=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        pages[3].addView(settingsPbPage)
        button(settingsPbPage,R.string.settings_back) { showApnSettings(false) }
        title(settingsPbPage,R.string.pb_settings_title)
        simPicker(settingsPbPage)
        val pbBox=panel(settingsPbPage)
        pbDefinitions=text(pbBox,"",11f,color=secondary)
        pbStatus=text(pbBox,"",12f,color=secondary)
        pbAcknowledge=button(pbBox,R.string.pb_acknowledge) {
            AlertDialog.Builder(this).setTitle(R.string.pb_acknowledge).setMessage(R.string.pb_acknowledge_confirm)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok) { _,_ -> RegionSession.acknowledgeLocalApnFailure();render() }.show()
        }
        pbApply=button(pbBox,R.string.pb_apply,true) { confirmLocalPb(false) }
        pbClear=button(pbBox,R.string.pb_clear) { confirmLocalPb(true) }
        pbRead=button(pbBox,R.string.pb_read) { readLocalPb() }
        pbReadback=text(pbBox,getString(R.string.pb_not_read),12f,color=secondary)
        val profileHeader=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        pages[1].addView(profileHeader,0);title(profileHeader,R.string.nav_profile)
        val selector=panel(profileHeader).apply { setPadding(dp(12),dp(8),dp(12),dp(8)) }
        simPicker(selector).also { textButton(it);(it.layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(2) }
        move(original,selector)
        // Results and read-only recovery actions are retained without operator activation controls.
        move(outcome,pages[1]);outcome.background=shape(surface,outline,radius=20)
        move(history,outcome);move(resultTarget,outcome,1)
        move(inspect,pages[2]);move(archiveV1,pages[2])
        title(pages[2],R.string.nav_journal)
        val journalHeading=pages[2].getChildAt(pages[2].childCount-1)
        move(journalHeading,pages[2],0)
        val operationBox=panel(pages[2])
        move(operationBox,pages[2],1)
        journalTitle=text(operationBox,"",16f,true)
        journalMetadata=text(operationBox,"",12f,color=secondary)
        journalSummary=text(operationBox,"",13f,color=secondary)
        journalSteps=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        operationBox.addView(journalSteps,params(6))
        journalDetailsButton=button(operationBox,R.string.journal_details) {
            AlertDialog.Builder(this).setTitle(R.string.journal_details).setMessage(journalDetailsText).setPositiveButton(android.R.string.ok,null).show()
        }
        val logActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        pages[2].addView(logActions,2)
        logActions.layoutParams=params(12)
        copyLogButton.setText(R.string.journal_copy_short)
        move(logButton,logActions,0);move(copyLogButton,logActions,1)
        listOf(logButton,copyLogButton).forEach { it.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(4) };it.textSize=13f;it.minHeight=dp(48);it.minimumHeight=dp(48);it.setSingleLine(true) }
        move(logView,pages[2],3)
        logView.visibility=View.GONE
        buildProfileOverview()
        val nav=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),dp(4),dp(4),dp(4)); background=shape(surface,radius=0)
        }
        outer.addView(nav,LinearLayout.LayoutParams(-1,-2))
        val labels=listOf(R.string.nav_home,R.string.nav_profile,R.string.nav_journal,R.string.nav_settings)
        labels.forEachIndexed { index,label ->
            val item=Button(this).apply {
                setText(label); isAllCaps=false; textSize=11f; minWidth=0; minimumWidth=0; minHeight=dp(60); minimumHeight=dp(60)
                gravity=Gravity.CENTER; setPadding(dp(2),dp(6),dp(2),dp(6)); maxLines=2
                setOnClickListener { showPage(index) }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,-2,1f)); navigationItems.add(item)
        }
        saved?.getIntArray("page_scroll")?.takeIf { it.size==4 }?.copyInto(pageScroll)
        selectedPage=-1
        showPage((saved?.getInt("page") ?: 0).coerceIn(0,3))
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if(selectedPage==3 && (settingsApnOpen || settingsPbOpen)) showApnSettings(false) else if(selectedPage!=0) showPage(0) else finish()
        }
    }
    /** Profile actions are bound to the selected SIM; details can be viewed for either operator. */
    private fun buildProfileOverview() {
        val page=pages[1]
        val old=(1 until page.childCount).map { page.getChildAt(it) }
        old.forEach { page.removeView(it) }
        profileEmpty=text(page,"",13f,color=secondary).apply { setPadding(dp(12),dp(12),dp(12),dp(12));background=shape(tonal,radius=16) }
        carrierRefusalAck=button(page,R.string.persist_refusal_ack) { RegionSession.acknowledgeCarrierRefusal();render() }
        text(page,getString(R.string.profiles_intro),15f,true)
        profileList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(profileList,params(0))
        for(profile in BundledProfiles.available) {
            val carrierCard=panel(profileList).apply { setPadding(dp(14),dp(14),dp(14),dp(10)) }
            profileCardViews[profile.id]=carrierCard
            val heading=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
            carrierCard.addView(heading,params(6))
            operatorLogo(heading,profile.id)
            val identity=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            heading.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
            text(identity,profileName(profile.id),18f,true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(3) }
            text(identity,"${profile.mcc}${profile.mnc} · v${LocalPbProfile.load(this,profile.id).version}",11f,color=secondary).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(3) }
            text(identity,getString(if(profile.experimental) R.string.compact_experimental else if(profile.id=="a1_by") R.string.profiles_a1_tested else R.string.compact_untested),11f,color=if(profile.id=="a1_by") palette.success else secondary).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin=0 }
            button(heading,R.string.design_details) { showProfileDetails(profile.id) }.apply {
                textButton(this);textSize=12f;minimumWidth=0;minWidth=0;minimumHeight=dp(44);minHeight=dp(44)
                layoutParams=LinearLayout.LayoutParams(dp(78),-2).apply { marginStart=dp(5) }
                setPadding(dp(3),dp(6),dp(3),dp(6))
            }
            text(carrierCard,getString(if(profile.id=="t2_ru") R.string.compact_t2_features else if(profile.id in setOf("megafon_ru","beeline_ru")) R.string.compact_beeline_features else if(profile.id=="mts_ru") R.string.compact_ru_features else R.string.compact_by_features),12f,color=secondary).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END }
            profileMatchLabels[profile.id]=text(carrierCard,"",12f,true,color=secondary)
            profileApplyButtons[profile.id]=button(carrierCard,R.string.overview_apply,true) {
                if(localPbPlan()?.configMode==profile.applyMode) {
                    if(ReaderSession.accessError()!=null) authorizeShizuku() else confirmLocalPb(false)
                }
            }
            val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            carrierCard.addView(actions,params(0));profileActionRows[profile.id]=actions
            profileCheckButtons[profile.id]=rowButton(actions,R.string.clean_check) { if(localPbPlan()?.configMode==profile.applyMode) readLocalPb() }.also { textButton(it) }
        }
        localApply=profileApplyButtons.getValue("a1_by")
        // The latest operation and legacy recovery details belong to Journal, not the selected SIM card.
        (outcome.parent as? ViewGroup)?.removeView(outcome)
        pages[2].addView(outcome,2)
    }
    private fun operatorLogo(parent:LinearLayout,id:String) {
        val image=ImageView(this).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageResource(when(id) { "t2_ru" -> R.drawable.operator_t2; "megafon_ru" -> R.drawable.operator_megafon; "beeline_ru" -> R.drawable.operator_beeline; "a1_by" -> R.drawable.operator_a1; "mts_by" -> R.drawable.operator_mts_by; else -> R.drawable.operator_mts_ru })
            if(id=="a1_by") { background=shape(Color.WHITE,radius=8);setPadding(dp(4),dp(4),dp(4),dp(4)) }
            if(id=="mts_by") { background=shape(0xFFE30613.toInt(),radius=8);setPadding(dp(3),dp(3),dp(3),dp(3)) }
        }
        parent.addView(image,LinearLayout.LayoutParams(dp(44),dp(44)).apply { marginEnd=dp(11) })
    }
    private fun buildHomeProfileOffer() {
        homeProfileCard=panel(pages[0]).apply { orientation=LinearLayout.HORIZONTAL;setPadding(dp(14),dp(14),dp(14),dp(10));background=shape(tonal,radius=24);visibility=View.GONE }
        pages[0].removeView(homeProfileCard)
        val before=imsPanels.values.map { pages[0].indexOfChild(it) }.filter { it>=0 }.minOrNull() ?: pages[0].childCount
        pages[0].addView(homeProfileCard,before)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        homeProfileCard.addView(content,LinearLayout.LayoutParams(0,-2,1f))
        homeProfileBadge=text(content,getString(R.string.home_profile_experimental),11f,true,color=secondary)
        homeProfileTitle=text(content,"",15f,true)
        homeProfileSubtitle=text(content,"",12f,color=secondary)
        homeProfileOpen=button(content,R.string.home_profile_open) {
            val sub=selected() ?: return@button
            val profile=BundledProfiles.find(sub.mccString,sub.mncString) ?: return@button
            showPage(1)
            pageScrollView.post {
                val target=profileCardViews[profile.id] ?: return@post
                val rect=android.graphics.Rect(0,0,target.width,target.height)
                pageScrollView.offsetDescendantRectToMyCoords(target,rect)
                pageScrollView.smoothScrollTo(0,(rect.top-dp(12)).coerceAtLeast(0))
            }
        }.apply {
            textButton(this);gravity=Gravity.START or Gravity.CENTER_VERTICAL
            minimumHeight=dp(40);minHeight=dp(40);setPadding(0,dp(5),dp(8),dp(5))
            (layoutParams as LinearLayout.LayoutParams).bottomMargin=0
        }
    }
    private fun renderHomeProfileOffer() {
        if(!::homeProfileCard.isInitialized) return
        val plan=localPbPlan()
        val profile=BundledProfiles.forMode(plan?.configMode)
        homeProfileCard.visibility=if(profile==null) View.GONE else View.VISIBLE
        if(profile==null || plan==null) return
        val lastChange=RegionSession.history(selectedSlot)?.time ?: 0L
        val values=profileValues.takeIf { pbReadTarget==plan && !RegionSession.busy && profileValuesAt>=lastChange }
        val checkState=profileCheckState(plan)
        val verified=checkState==ProfileOverviewRules.CheckState.ACTIVE
        val name=profileName(profile.id)
        homeProfileBadge.visibility=if(profile.experimental) View.VISIBLE else View.GONE
        homeProfileTitle.text=getString(if(verified) R.string.home_profile_applied else if(checkState==ProfileOverviewRules.CheckState.FAILED) R.string.check_home_failed else R.string.home_profile_available,name)
        homeProfileSubtitle.setText(when(checkState) {
            ProfileOverviewRules.CheckState.ACTIVE -> R.string.home_profile_verified
            ProfileOverviewRules.CheckState.DIFFERENT -> R.string.check_state_different
            ProfileOverviewRules.CheckState.FAILED -> R.string.check_read_error_note
            else -> R.string.home_profile_description
        })
        homeProfileOpen.isEnabled=true
    }
    private fun profileName(id:String)=getString(when(id) { "t2_ru" -> R.string.profiles_t2_name; "megafon_ru" -> R.string.profiles_megafon_name; "beeline_ru" -> R.string.profiles_beeline_name; "mts_by" -> R.string.profiles_mts_name; "mts_ru" -> R.string.profiles_mts_ru_name; else -> R.string.overview_a1 })
    private fun profileDefinitions(id:String):String {
        val profile=LocalPbProfile.load(this,id)
        val apns=getString(if(id=="t2_ru") R.string.profiles_t2_apns else if(id=="megafon_ru") R.string.profiles_megafon_apns else if(id=="beeline_ru") R.string.profiles_beeline_apns else if(id=="mts_by") R.string.profiles_mts_apns else if(id=="mts_ru") R.string.profiles_mts_ru_apns else R.string.pb_help)
        val definitions=(profile.flags+profile.integers+profile.strings+profile.arrays).map { (key,value) -> "$key = $value" }.joinToString("\n")
        return (if(BundledProfiles.available.single { it.id==id }.experimental) getString(R.string.profiles_experimental)+"\n\n" else "")+"$id.pb · v${profile.version}\n\n$apns\n\n$definitions"
    }
    private fun differenceName(key:String)=when(key) {
        "carrier_volte_available_bool" -> "VoLTE"
        "carrier_wfc_ims_available_bool" -> "VoWiFi"
        "carrier_vt_available_bool","vendor.ims.carrier_video_over_lte_bool" -> "VT"
        "carrier_supports_ss_over_ut_bool" -> "UT"
        "carrier_ussd_method_int" -> "USSD"
        "imssms.sms_over_ims_supported_bool" -> "SMS IMS"
        "show_4g_for_lte_data_icon_bool","hide_lte_plus_data_icon_bool" -> "4G/4G+"
        LocalPbRules.XCAP_SERVER_KEY -> "XCAP"
        LocalPbRules.XCAP_APN_KEY -> "XCAP APN"
        "imsss.ut_as_server_port_int" -> "XCAP port"
        LocalPbRules.BSF_KEY -> "BSF"
        LocalPbRules.BSF_PORT_KEY -> "BSF port"
        "iwlan.epdg_static_address_string" -> "ePDG"
        "iwlan.epdg_address_priority_int_array" -> "ePDG priority"
        LocalPbRules.VOICEMAIL_KEY -> getString(R.string.details_voicemail)
        else -> key
    }
    private fun showProfileDetails(id:String=BundledProfiles.forMode(localPbPlan()?.configMode)?.id ?: "a1_by") {
        val descriptor=BundledProfiles.available.single { it.id==id }
        val profile=LocalPbProfile.load(this,id)
        val scroll=ScrollView(this)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),dp(8)) }
        scroll.addView(body)
        text(body,"${profile.name} · v${profile.version} · ${descriptor.mcc}${descriptor.mnc}",12f,color=secondary)
        if(descriptor.experimental) text(body,getString(R.string.profiles_experimental),12f,true,color=secondary)
        val status=text(body,"",14f,true).apply { setPadding(dp(12),dp(10),dp(12),dp(10));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val checkedAt=text(body,"",11f,color=secondary)
        val differencesText=text(body,"",12f,color=secondary).apply { visibility=View.GONE }
        text(body,getString(R.string.details_main),15f,true)
        fun row(label:String,value:String) {
            val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP }
            body.addView(line,params(7))
            text(line,label,13f,color=secondary).layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(10) }
            text(line,value,13f,true).apply { gravity=Gravity.END;layoutParams=LinearLayout.LayoutParams(0,-2,1.3f) }
        }
        fun flag(label:String,key:String) = row(label,getString(when(profile.flags[key]) { true -> R.string.details_enabled;false -> R.string.details_disabled;else -> R.string.details_default }))
        flag("VoLTE","carrier_volte_available_bool")
        flag("VoWiFi","carrier_wfc_ims_available_bool")
        flag(getString(R.string.capability_video),"carrier_vt_available_bool")
        flag("UT / XCAP","carrier_supports_ss_over_ut_bool")
        if("imssms.sms_over_ims_supported_bool" in profile.flags) flag(getString(R.string.details_sms_ims),"imssms.sms_over_ims_supported_bool")
        row("USSD",getString(if(profile.integers["carrier_ussd_method_int"]==1) R.string.details_ussd_ims_cs else R.string.details_default))
        row("4G / 4G+",getString(if(profile.flags["show_4g_for_lte_data_icon_bool"]==true && profile.flags["hide_lte_plus_data_icon_bool"]==false) R.string.details_enabled else R.string.details_default))
        text(body,getString(R.string.overview_apns),15f,true)
        val definitions=when(id) { "t2_ru" -> T2RuApnRules.definitions; "megafon_ru" -> MegafonRuApnRules.definitions; "beeline_ru" -> BeelineRuApnRules.definitions; "mts_by" -> MtsByApnRules.definitions;"mts_ru" -> MtsRuApnRules.definitions;else -> LocalPbApnRules.definitions }
        for(d in definitions) {
            val label=getString(when(d.type) { "default","default,supl" -> R.string.overview_internet;"mms" -> R.string.overview_mms;"ims" -> R.string.overview_ims;else -> R.string.overview_xcap })
            val protocol=if(d.protocol=="IPV4V6") "IPv4/IPv6" else "IPv4"
            row(label,"${d.apn} · $protocol")
        }
        definitions.firstOrNull { it.type=="mms" && it.mmsc.isNotEmpty() }?.let { d ->
            text(body,"MMSC: ${d.mmsc}"+if(d.mmsproxy.isNotEmpty()) " · ${d.mmsproxy}:${d.mmsport}" else "",11f,color=secondary)
        }
        if(profile.strings[LocalPbRules.XCAP_SERVER_KEY]!=null || profile.strings[LocalPbRules.BSF_KEY]!=null) {
            text(body,getString(R.string.details_servers),15f,true)
            profile.strings[LocalPbRules.XCAP_SERVER_KEY]?.let { host ->
                text(body,"XCAP: $host"+(profile.integers["imsss.ut_as_server_port_int"]?.let { ":$it" } ?: ""),12f,color=secondary)
            }
            profile.strings[LocalPbRules.BSF_KEY]?.let { host ->
                text(body,"BSF: $host"+(profile.integers[LocalPbRules.BSF_PORT_KEY]?.let { ":$it" } ?: ""),12f,color=secondary)
            }
        }
        profile.strings["iwlan.epdg_static_address_string"]?.let { host ->
            text(body,"VoWiFi · ePDG",15f,true)
            text(body,host,12f,color=secondary)
            text(body,getString(R.string.details_epdg_selection),12f,color=secondary)
        }
        profile.strings[LocalPbRules.VOICEMAIL_KEY]?.let { row(getString(R.string.details_voicemail),it) }
        text(body,getString(R.string.details_service_note),11f,color=secondary)
        val technical=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        val more=button(body,R.string.overview_technical) {
            technical.visibility=if(technical.visibility==View.GONE) View.VISIBLE else View.GONE
        }.also { textButton(it) }
        body.addView(technical)
        text(technical,profileDefinitions(id),11f,color=secondary).apply { typeface=Typeface.MONOSPACE;setTextIsSelectable(true) }
        val technicalDifferences=text(technical,"",11f,color=secondary).apply { typeface=Typeface.MONOSPACE;setTextIsSelectable(true);visibility=View.GONE }
        val dialog=AlertDialog.Builder(this).setTitle(getString(R.string.profiles_details_title,profileName(id)))
            .setView(scroll).setPositiveButton(android.R.string.ok,null).setNeutralButton(R.string.clean_check,null).create()
        val clear=button(body,R.string.check_remove_overrides) {
            if(localPbPlan()?.configMode==descriptor.applyMode) { dialog.dismiss();confirmLocalPb(true) }
        }.also { textButton(it) }
        fun refresh(canRead:Boolean) {
            val plan=localPbPlan()?.takeIf { it.configMode==descriptor.applyMode }
            val phone=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
            val readable=plan!=null && phone && !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running && !ApnSession.pending
            if(canRead && readable) readLocalPb()
            val lastChange=RegionSession.history(selectedSlot)?.time ?: 0L
            val values=profileValues.takeIf { plan!=null && pbReadTarget==plan && phone && !RegionSession.busy && profileValuesAt>=lastChange }
            val state=profileCheckState(plan)
            val verified=state==ProfileOverviewRules.CheckState.ACTIVE
            status.setText(if(plan==null) R.string.details_other_sim else checkStateLabel(state))
            val differences=if(state==ProfileOverviewRules.CheckState.DIFFERENT) ProfileOverviewRules.differencesFor(values,id) else emptyList()
            differencesText.visibility=if(differences.isNotEmpty() || state==ProfileOverviewRules.CheckState.FAILED) View.VISIBLE else View.GONE
            differencesText.text=if(state==ProfileOverviewRules.CheckState.FAILED) getString(R.string.check_read_error_note) else
                getString(R.string.check_diff_summary,differences.size,differences.take(4).joinToString(", ") { differenceName(it.key) })+(if(differences.size>4) " …" else "")
            technicalDifferences.visibility=if(differences.isNotEmpty() || state==ProfileOverviewRules.CheckState.FAILED) View.VISIBLE else View.GONE
            technicalDifferences.text=if(state==ProfileOverviewRules.CheckState.FAILED) profileReadError.orEmpty() else getString(R.string.check_differences)+"\n"+differences.joinToString("\n") { d ->
                d.key+": "+getString(R.string.check_diff_value,d.expected.toString(),d.actual?.toString() ?: getString(R.string.check_missing_value))
            }
            clear.visibility=if(plan!=null) View.VISIBLE else View.GONE
            clear.isEnabled=plan!=null && phone && ReaderSession.accessError()==null && RegionSession.canApplyDirect(localPbPlan(true))
            status.setTextColor(if(verified) palette.success else secondary)
            status.background=shape(if(verified) palette.successSurface else tonal,radius=14)
            checkedAt.text=when {
                plan==null -> getString(R.string.profiles_mismatch,descriptor.mcc+descriptor.mnc)
                state!=ProfileOverviewRules.CheckState.NOT_CHECKED -> getString(if(state==ProfileOverviewRules.CheckState.FAILED) R.string.check_attempt_at else R.string.details_verified_at,plan.slot+1,DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(profileValuesAt)))
                else -> getString(R.string.details_check_note)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled=readable
            more.setText(if(technical.visibility==View.VISIBLE) R.string.details_hide_technical else R.string.overview_technical)
        }
        more.setOnClickListener {
            technical.visibility=if(technical.visibility==View.GONE) View.VISIBLE else View.GONE
            refresh(false)
        }
        dialog.setOnShowListener {
            refresh(true)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { refresh(true) }
        }
        dialog.show()
    }
    private fun renderProfileOverview() {
        if(!::localApply.isInitialized) return
        val plan=localPbPlan()
        val selectedProfile=BundledProfiles.forMode(plan?.configMode)
        val lastChange=RegionSession.history(selectedSlot)?.time ?: 0L
        val values=profileValues.takeIf { plan!=null && pbReadTarget==plan && !RegionSession.busy && profileValuesAt>=lastChange }
        val checkState=profileCheckState(plan)
        val verified=checkState==ProfileOverviewRules.CheckState.ACTIVE
        val phone=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
        val ready=plan!=null && phone && ReaderSession.accessError()==null && RegionSession.canApplyDirect(plan)
        val readable=plan!=null && phone && !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running && !ApnSession.pending
        profileEmpty.visibility=if(selectedProfile==null) View.VISIBLE else View.GONE
        profileEmpty.text=selected()?.let { getString(R.string.compact_no_profile,it.displayName.toString()) } ?: getString(R.string.compact_choose_sim)
        val ordered=BundledProfiles.available.sortedBy { if(it.id==selectedProfile?.id) 0 else 1 }
        val key=ordered.joinToString(",") { it.id }
        if(profileOrderKey!=key) {
            ordered.forEach { profile ->
                val view=profileCardViews.getValue(profile.id)
                profileList.removeView(view);profileList.addView(view)
            }
            profileOrderKey=key
        }
        for(profile in BundledProfiles.available) {
            val matches=profile.id==selectedProfile?.id
            val apply=profileApplyButtons.getValue(profile.id)
            apply.visibility=if(matches) View.VISIBLE else View.GONE
            val missingAccess=ReaderSession.accessError()!=null
            apply.isEnabled=matches && (ready || phone && missingAccess && !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running && !ApnSession.pending)
            apply.setText(if(matches && missingAccess) R.string.authorize_shizuku else if(matches && verified) R.string.compact_reapply else R.string.overview_apply)
            if(matches && verified) textButton(apply) else tintButton(apply,true)
            profileActionRows.getValue(profile.id).visibility=if(matches) View.VISIBLE else View.GONE
            profileCheckButtons.getValue(profile.id).isEnabled=matches && readable
            val operation=RegionSession.activePlan?.takeIf { it.slot==plan?.slot && it.subId==plan?.subId && it.configMode in setOf(profile.applyMode,profile.clearMode) }
            val needsAttention=operation!=null && !RegionSession.busy && RegionSession.code!="MODIFIER_REPORTED_SUCCESS" && (RegionSession.code in RegionGuard.completedApply || RegionSession.code=="OUTCOME_UNCERTAIN")
            val status=profileMatchLabels.getValue(profile.id)
            status.visibility=if(matches) View.VISIBLE else View.GONE
            status.setText(when {
                matches && ReaderSession.accessError()!=null -> R.string.profile_need_access
                matches && verified -> R.string.check_state_active
                matches && RegionSession.busy && RegionSession.activePlan?.slot==plan?.slot && RegionSession.activePlan?.subId==plan?.subId -> R.string.pb_working
                matches && checkState!=ProfileOverviewRules.CheckState.NOT_CHECKED -> checkStateLabel(checkState)
                matches && needsAttention -> R.string.compact_result_attention
                matches && !phone -> R.string.profile_need_access
                else -> R.string.overview_not_checked
            })
            status.setTextColor(if(matches && verified) palette.success else secondary)
            profileCardViews.getValue(profile.id).background=shape(if(matches) tonal else surface,if(matches) blue else Color.TRANSPARENT,radius=22)
        }
        carrierRefusalAck.visibility=if(RegionSession.canAcknowledgeCarrierRefusal()) View.VISIBLE else View.GONE
    }
    private fun showPage(index: Int) {
        if(pages.isEmpty() || index !in pages.indices || selectedPage==index) return
        if(selectedPage in pages.indices) { pageScroll[selectedPage]=pageScrollView.scrollY; saveDraft() }
        if(index!=0 && imsSlot>=0) closeIms()
        currentFocus?.clearFocus()
        window.insetsController?.hide(WindowInsets.Type.ime())
        selectedPage=index
        pages.forEachIndexed { i,page -> page.visibility=if(i==index) View.VISIBLE else View.GONE }
        navigationItems.forEachIndexed { i,item ->
            val active=i==index
            item.isSelected=active
            item.background=RippleDrawable(ColorStateList.valueOf((blue and 0x00FFFFFF) or 0x22000000),shape(Color.TRANSPARENT),null)
            item.backgroundTintList=null
            item.setTextColor(if(active) blue else secondary)
            item.setTypeface(null,if(active) Typeface.BOLD else Typeface.NORMAL)
            // A high-contrast pill stays visible even with a monochrome wallpaper palette.
            val icon=android.graphics.drawable.LayerDrawable(arrayOf(
                shape(if(active) blue else Color.TRANSPARENT,radius=16),
                NavigationIconDrawable(i,if(active) palette.onPrimary else secondary)
            ))
            icon.setLayerInset(1,dp(21),dp(5),dp(21),dp(5))
            icon.setBounds(0,0,dp(64),dp(32))
            item.setCompoundDrawables(null,icon,null,null);item.compoundDrawablePadding=dp(3)
            item.stateListAnimator=null;item.elevation=0f
        }
        pageScrollView.post { if(selectedPage==index) pageScrollView.scrollTo(0,pageScroll[index]) }
        if(::localApply.isInitialized) { render();if(index in 0..1) requestProfileCheck() }
    }
    private fun closeIms() {
        pendingNumberTarget = null; pendingCapabilityId = null
        imsNumbers.values.forEach { it.text = "" }
        imsNumberNotes.values.forEach { it.text = "" }
        ImsNumberSession.close(); imsTarget = null; imsSlot = -1
    }
    private fun validIms(t: ImsNumberSession.Target) = subscriptions.singleOrNull { it.simSlotIndex == t.slot }?.subscriptionId == t.subId
    private fun buildImsSpoilers() {
        for (slot in 0..1) {
            val box = panel().apply { setPadding(dp(12), dp(4), dp(12), dp(4)) }
            imsPanels[slot]=box
            imsHeaders[slot] = text(box,"",14f,true).apply {
                minimumHeight=dp(42); gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(4),dp(8),dp(4),dp(8)); setAccessibilityHeading(true)
                layoutParams=LinearLayout.LayoutParams(-1,-2)
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(body); imsBodies[slot] = body
            imsTexts[slot] = text(body, "", 12f, color = secondary)
            val labels = listOf(R.string.pixel_registration, R.string.pixel_lte, R.string.pixel_nr, R.string.pixel_wifi,
                R.string.capability_cross, R.string.capability_video, R.string.capability_ut)
            imsRows[slot] = labels.map { label ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
                body.addView(row, LinearLayout.LayoutParams(-1, -2))
                text(row, getString(label), 13f).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1.15f).apply { marginEnd = dp(12) } }
                val value = text(row, "", 13f, true).apply { gravity = Gravity.CENTER; maxWidth = dp(170); layoutParams = LinearLayout.LayoutParams(-2, -2) }
                body.addView(View(this).apply { setBackgroundColor(outline) }, LinearLayout.LayoutParams(-1, dp(1)))
                value
            }
            val numberRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(9))
            }
            body.addView(numberRow, LinearLayout.LayoutParams(-1, -2))
            text(numberRow, getString(R.string.ims_phone_row), 13f).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.15f).apply { marginEnd = dp(12) }
            }
            imsNumbers[slot] = text(numberRow, "", 13f, true).apply { gravity = Gravity.END }
            imsNumberNotes[slot] = text(body, "", 11f, color = secondary)
            val refresh = button(body, R.string.home_refresh, filled = true) { refreshImsStatus() }.apply {
                val icon = getDrawable(R.drawable.ic_refresh)?.mutate()
                icon?.setBounds(0, 0, dp(22), dp(22))
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = dp(10)
                compoundDrawableTintList = textColors
                addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                    val groupWidth = paint.measureText(text.toString()).toInt() + dp(32)
                    val inset = maxOf(dp(10), ((right - left) - groupWidth) / 2)
                    if (paddingStart != inset) setPaddingRelative(inset, dp(9), inset, dp(9))
                }
            }
            imsActions[slot] = listOf(refresh)
            body.visibility = View.GONE
        }
    }
    private fun renderImsSpoilers() {
        val desired=if(visible && selectedPage==0) selected()?.let { ImsNumberSession.Target(it.simSlotIndex,it.subscriptionId) } else null
        if(imsTarget!=desired) {
            closeIms(); imsTarget=desired; imsSlot=desired?.slot ?: -1
            desired?.let { ImsNumberSession.show(it) }
        }
        imsTarget?.takeIf { !validIms(it) }?.let {
            pendingNumberTarget = null; pendingCapabilityId = null
            ImsNumberSession.close(); imsTarget = null
            imsNumbers.values.forEach { view -> view.text = "" }; imsNumberNotes.values.forEach { view -> view.text = "" }
        }
        for (slot in 0..1) {
            imsPanels[slot]?.visibility = if(slot==selectedSlot) View.VISIBLE else View.GONE
            val open = slot==selectedSlot
            val subId = subscriptions.singleOrNull { it.simSlotIndex == slot }?.subscriptionId
            val snapshotHeader = ReaderSession.state.archive.entries.firstOrNull { it.key == ReadResultKey(slot, subId ?: -1, ReadOperation.CAPABILITIES) }?.value
            imsHeaders[slot]?.text = getString(R.string.home_ims_title, slot + 1)
            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (snapshotHeader != null && !snapshotHeader.restored && snapshotHeader.result.error == null && snapshotHeader.result.transport?.state == 2) palette.success else secondary); setSize(dp(9), dp(9)); setBounds(0, 0, dp(9), dp(9)) }
            imsHeaders[slot]?.apply { setCompoundDrawablesRelative(dot, null, null, null); compoundDrawablePadding = dp(10); setTextColor(ink) }
            imsBodies[slot]?.visibility = if (open) View.VISIBLE else View.GONE
            if (!open) continue
            val target = imsTarget
            val ready = visible && selectedPage==0 && target != null && ReaderSession.accessError() == null && !ReaderSession.state.running
            imsActions[slot]?.forEach { it.isEnabled = ready }
            if (target == null) {
                imsTexts[slot]?.setText(if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) R.string.classic_phone_needed else if(subId==null) R.string.compact_choose_sim else R.string.capability_sim_changed)
                imsRows[slot]?.forEach { it.setText(R.string.unknown); it.setTextColor(secondary) }
                imsNumbers[slot]?.text = ""; imsNumberNotes[slot]?.text = ""
                continue
            }
            val key = ReadResultKey(slot, target.subId, ReadOperation.CAPABILITIES)
            val snapshot = ReaderSession.state.archive.entries.firstOrNull { it.key == key }?.value
            val busy = ReaderSession.state.busy
            val checking = busy?.slot == slot && busy.subId == target.subId && busy.operation == ReadOperation.CAPABILITIES
            val whenText = snapshot?.let { getString(if (it.restored) R.string.home_saved_time else R.string.home_session_time,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.finishedAt))) } ?: getString(R.string.capability_not_checked)
            imsTexts[slot]?.text = listOfNotNull(if (checking) getString(R.string.read_running) else null,
                whenText, snapshot?.result?.error?.let { getString(R.string.classic_api_error, it) }).joinToString("\n")
            val data = snapshot?.result?.takeIf { it.error == null }
            val caps = data?.capabilities
            val registration = getString(when (data?.transport?.state) {
                0 -> R.string.mmtel_unregistered; 1 -> R.string.mmtel_registering; 2 -> R.string.mmtel_registered; else -> R.string.unknown
            })
            val flags = listOf(caps?.values?.get("voice_lte"), caps?.values?.get("voice_nr"), caps?.values?.get("voice_wifi"),
                caps?.values?.get("voice_cross_sim"), caps?.let { CapabilityRules.aggregate(it, "video") }, caps?.let { CapabilityRules.aggregate(it, "ut") })
            imsRows[slot]?.forEachIndexed { index, value ->
                val flag = if (index == 0) data?.transport?.state?.let { it == 2 } else flags[index - 1]
                value.text = if (index == 0) registration else availability(flag)
                value.setPadding(dp(8),dp(3),dp(8),dp(3))
                value.background=shape(if(flag==true) palette.successSurface else tonal,radius=10)
                value.setTextColor(if (flag == true) palette.success else secondary)
            }
            renderImsNumber(key)
        }
        completeImsRefresh()
    }
    private fun refreshImsStatus() {
        val target = imsTarget ?: return
        if (!visible || !validIms(target) || ReaderSession.state.running || ReaderSession.accessError() != null) return
        pendingNumberTarget = null; pendingCapabilityId = null
        ImsNumberSession.show(target)
        val started = System.currentTimeMillis()
        ReaderSession.read(this, ReadOperation.CAPABILITIES, target.slot, target.subId)
        val request = ReaderSession.state.busy ?: ReaderSession.state.latest?.request
        if (request != null && request.operation == ReadOperation.CAPABILITIES && request.slot == target.slot &&
            request.subId == target.subId && request.startedAt >= started) {
            pendingNumberTarget = target; pendingCapabilityId = request.id
        }
        renderImsSpoilers()
    }
    private fun completeImsRefresh() {
        val target = pendingNumberTarget ?: return
        val expected = pendingCapabilityId ?: return
        val done = ReaderSession.state.archive.entries.firstOrNull {
            it.key == ReadResultKey(target.slot, target.subId, ReadOperation.CAPABILITIES)
        }?.value?.takeIf { it.request.id == expected } ?: return
        // Consume this one explicit refresh before starting the private number read.
        pendingNumberTarget = null; pendingCapabilityId = null
        if (ImsRefreshRules.readNumber(expected, done.request.id, imsTarget == target && validIms(target),
                visible, ReaderSession.state.running, done.result.error != null))
            ImsNumberSession.read(this, target)
    }
    private fun renderImsNumber(key: ReadResultKey) {
        val target = ImsNumberSession.Target(key.slot, key.subId)
        val result = if (ImsNumberSession.isVisible(target)) ImsNumberSession.result else null
        val waiting = ImsNumberSession.busy == target || pendingNumberTarget == target
        imsNumbers[key.slot]?.text = when {
            waiting -> getString(R.string.ims_phone_wait)
            result == null -> getString(R.string.ims_phone_not_read)
            result.status == "FOUND" -> result.numbers.joinToString("\n")
            else -> getString(R.string.ims_phone_unavailable)
        }
        imsNumberNotes[key.slot]?.text = when {
            waiting -> getString(if (ImsNumberSession.waitingLong) R.string.identity_wait_long else R.string.ims_phone_wait_note)
            result == null -> getString(R.string.ims_phone_refresh_hint)
            result.status == "FOUND" -> getString(R.string.ims_phone_source, DateFormat.getDateTimeInstance().format(Date(ImsNumberSession.receivedAt)))
            else -> getString(when (result.status) {
                "NOT_REGISTERED" -> R.string.identity_no_registration
                "NO_NUMBER" -> R.string.identity_no_number
                "NO_URIS" -> R.string.identity_no_uris
                "TIMEOUT" -> R.string.identity_timeout
                "STATE_CHANGED", "MAPPING_CHANGED" -> R.string.identity_changed
                "CLEANUP_FAILED" -> R.string.identity_cleanup_failed
                else -> R.string.ims_phone_read_error
            })
        }
    }
    private fun availability(value: Boolean?): String = getString(when (value) {
        true -> R.string.capability_available
        false -> R.string.capability_unavailable
        null -> R.string.unknown
    })

    private fun apnTarget(info: SubscriptionInfo? = selected()): ApnTarget? {
        val sub = info ?: return null
        val p = RegionPlan(sub.simSlotIndex, sub.subscriptionId, sub.displayName.toString().take(100),
            sub.mccString ?: "", sub.mncString ?: "", sub.isEmbedded, sub.mccString ?: "", sub.mncString ?: "")
        return if (p.valid() && sub.cardId >= 0) ApnTarget(p, sub.cardId) else null
    }
    private fun buildApn() {
        val box = panel(root)
        apnTitle = text(box, "", 18f, true).apply { visibility=View.GONE }
        apnToggle=Switch(this).apply {
            text=getString(R.string.apn_auto_toggle); textSize=15f; setTextColor(ink); minHeight=dp(48)
        }
        box.addView(apnToggle,params(6))
        apnToggle.setOnCheckedChangeListener { _, checked ->
            if(!renderingApnToggle) apnTarget()?.let { target ->
                ApnSession.setRestoreEnabled(target,checked); render()
            }
        }
        apnToggleHint=text(box,"",12f,color=secondary)
        apnSummary=text(box,"",12f,color=secondary).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END }
        apnExtraButton=button(box,R.string.apn_more) {
            showPage(3)
            showApnSettings(true)
        }.also { textButton(it) }
        apnExtra=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        box.addView(apnExtra,params(0))
        apnInfo = text(apnExtra, "", 13f)
        apnRead = button(apnExtra, R.string.apn_read) { apnTarget()?.let { ApnSession.read(this, it) } }.also { textButton(it) }
        apnSave = button(apnExtra, R.string.apn_save) {
            apnTarget()?.let { target ->
                AlertDialog.Builder(this).setTitle(R.string.apn_save).setMessage(getString(R.string.apn_save_confirm, target.plan.operator, target.plan.slot + 1))
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.apn_save) { _, _ ->
                        if (ApnSession.same(apnTarget(), target)) ApnSession.save(this, target)
                    }.show()
            }
        }
        apnRestore = button(apnExtra, R.string.apn_restore_generic, true) {
            if (ApnSession.pending) ApnSession.check(this)
            else apnTarget()?.let { target ->
                val p = ApnSession.preview?.takeIf { ApnSession.same(it.target, target) }
                if (p == null && ApnSession.same(ApnSession.target,target) && ApnSession.choices.isNotEmpty()) {
                    val choices=ApnSession.choices.toList()
                    AlertDialog.Builder(this).setTitle(R.string.apn_choose_match)
                        .setItems(choices.map { getString(R.string.apn_choice_row,it.name,it.id,it.apn,it.protocol) }.toTypedArray()) { _, index ->
                            if (ApnSession.same(apnTarget(),target)) ApnSession.choose(this,target,choices[index])
                        }.setNegativeButton(android.R.string.cancel,null).show()
                }
                else if (p == null) ApnSession.prepare(this, target)
                else AlertDialog.Builder(this).setTitle(getString(R.string.apn_restore_operator, target.plan.operator))
                    .setMessage(getString(R.string.apn_restore_confirm, target.plan.slot + 1, target.plan.subId,
                        p.current.name, p.current.apn, p.candidate.name, p.candidate.apn,
                        p.candidate.protocol, p.candidate.roaming) +
                        if (ApnSession.saved(target)?.automatic == true) "\n\n" + getString(R.string.apn_saved_auto_origin) else "")
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.apn_select) { _, _ ->
                        if (ApnSession.same(apnTarget(), target) && !ReaderSession.state.running) ApnSession.apply(this, p)
                    }.show()
            }
        }
        apnForget = button(apnExtra, R.string.apn_forget) {
            apnTarget()?.let { target ->
                AlertDialog.Builder(this).setTitle(R.string.apn_forget)
                    .setMessage(getString(R.string.apn_forget_confirm,target.plan.slot+1,target.plan.operator))
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(R.string.apn_forget_confirm_button) { _, _ ->
                        if(ApnSession.same(apnTarget(),target)) ApnSession.forget(target)
                    }.show()
            }
        }.also { textButton(it) }

        apnResult = text(box, "", 12f, color = secondary)
        textButton(button(apnExtra, R.string.pixel_apn_about) {
            AlertDialog.Builder(this).setTitle(R.string.pixel_apn_about).setMessage(R.string.apn_limit).setPositiveButton(android.R.string.ok, null).show()
        })
    }
    private fun renderApn() {
        if (!::apnTitle.isInitialized) return
        val target = apnTarget()
        val saved = ApnSession.saved(target)
        val name = target?.plan?.operator ?: getString(R.string.classic_unknown)
        renderingApnToggle=true
        apnToggle.isChecked=target?.let { ApnSession.restoreEnabled(it) } ?: false
        renderingApnToggle=false
        apnToggle.isEnabled=target!=null && !ReaderSession.state.running && !RegionSession.busy && !RegionSession.blocksReads && !ApnSession.pending
        apnToggleHint.setText(if(apnToggle.isChecked) R.string.compact_apn_on else R.string.compact_apn_off)
        apnSummary.text=if(saved==null) getString(R.string.profile_apn_missing)
            else getString(R.string.profile_apn_saved,saved.row.name,saved.row.apn)
        if(ApnSession.pending) apnExtra.visibility=View.VISIBLE
        apnExtraButton.setText(R.string.compact_apn_settings)
        apnTitle.text = getString(R.string.apn_heading, selectedSlot + 1, name)
        val row = if (ApnSession.same(target, ApnSession.target)) ApnSession.current else null
        val current = row?.let { getString(R.string.apn_row_details,it.name,it.apn,it.type,it.protocol,it.roaming,it.numeric.ifBlank { "—" }) } ?: getString(R.string.settings_apn_unread)
        val backup = saved?.let { getString(R.string.apn_saved_details,getString(R.string.apn_row_details,it.row.name,it.row.apn,it.row.type,it.row.protocol,it.row.roaming,it.row.numeric.ifBlank { "—" }),DateFormat.getDateTimeInstance().format(Date(it.time))) + "\n" + getString(if(it.automatic) R.string.apn_saved_auto_origin else R.string.apn_saved_manual_origin) } ?: getString(R.string.apn_no_saved)
        apnInfo.text = getString(R.string.apn_info, current, backup)
        val enabled = target != null && ReaderSession.accessError() == null && !ReaderSession.state.running && !RegionSession.blocksReads
        apnRead.isEnabled = enabled; apnSave.isEnabled = enabled
        apnForget.isEnabled = ApnSession.canForget(target)
        apnRestore.isEnabled = if (ApnSession.pending) !ApnSession.busy && !RegionSession.blocksReads && ReaderSession.accessError() == null else enabled && saved != null
        apnRestore.text = if (ApnSession.pending) getString(R.string.apn_check_pending, (ApnSession.target?.plan?.slot ?: 0) + 1)
            else if (ApnSession.same(target,ApnSession.target) && ApnSession.choices.isNotEmpty()) getString(R.string.apn_choose_match)
            else getString(R.string.apn_restore_now)
        val relevant = ApnSession.pending || ApnSession.same(target, ApnSession.target)
        apnResult.visibility = if (relevant) View.VISIBLE else View.GONE
        apnResult.text = getString(apnMessage(ApnSession.code))
        apnCode.text = getString(R.string.apn_result_code, ApnSession.code)
    }
    private fun apnMessage(code: String) = when (code) {
        "AUTO_RESTORING" -> R.string.apn_auto_restoring
        "RESTORE_ENABLED" -> R.string.apn_toggle_enabled_result
        "RESTORE_DISABLED" -> R.string.apn_toggle_disabled_result
        "SNAPSHOT_FORGOTTEN" -> R.string.apn_forgotten
        "AUTO_SAVING" -> R.string.apn_auto_working
        "AUTO_SAVED" -> R.string.apn_auto_saved
        "AUTO_PRESERVED" -> R.string.apn_auto_preserved
        "APN_SAVED_DATA_INVALID" -> R.string.apn_saved_invalid
        "WORKING" -> R.string.apn_working
        "READ" -> R.string.apn_read_ok
        "SAVED" -> R.string.apn_saved_ok
        "PREPARED" -> R.string.apn_prepared
        "APN_ALREADY_SELECTED" -> R.string.apn_already_selected
        "APN_CHOICE_REQUIRED" -> R.string.apn_choice_required
        "APN_VERIFIED" -> R.string.apn_verified
        "APN_NOT_SELECTED" -> R.string.apn_not_selected
        "APN_PENDING", "APN_NOT_TRACKED" -> R.string.apn_pending
        "APN_NO_UNIQUE_MATCH" -> R.string.apn_no_match
        "APN_NO_SNAPSHOT" -> R.string.apn_no_saved
        "APN_NOT_ORIGINAL" -> R.string.apn_not_original
        "MAPPING_CHANGED" -> R.string.apn_mapping_changed
        "STORE_FAILED" -> R.string.apn_store_failed
        "APN_ENDED_AFTER_REBOOT" -> R.string.apn_reboot
        "APN_TICKET_EXPIRED" -> R.string.apn_expired
        "NOT_CHECKED" -> R.string.capability_not_checked
        else -> R.string.apn_error
    }
    private fun buildTesting() {
        text(root, getString(R.string.pixel_service_section), 13f, true, secondary)
        textButton(button(root, R.string.pixel_about_device) { showAbout() })
        toolsButton = button(root, R.string.classic_more_tests) {
            animateDisclosure(); tools.visibility = if (tools.visibility == View.VISIBLE) View.GONE else View.VISIBLE; render()
        }
        tools = panel()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; tools.addView(row, params(9))
        rowButton(row, R.string.classic_diag_sim) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("slot", selectedSlot).putExtra("openStatus", true))
        }
        apiButton = rowButton(row, R.string.classic_api_check) { checkApi() }
        apiResult = text(tools, "", 13f, color = secondary)
        apnCode = text(tools, "", 12f, color = secondary)
        button(tools, R.string.region_refresh) { reload(); log("SIM_REFRESH") }
        button(tools, R.string.authorize_shizuku) { authorizeShizuku() }
        button(tools, R.string.classic_diag_open) { startActivity(Intent(this, MainActivity::class.java).putExtra("slot", selectedSlot)) }
        radioTime = text(tools, "", 12f, color = secondary)
        text(tools, getString(R.string.classic_catalog_note), 12f, color = secondary)
        text(tools, getString(R.string.ui_version, packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName), 12f, color = secondary)
        logButton = button(tools, R.string.classic_show_log) {
            animateDisclosure(); logView.visibility = if (logView.visibility == View.VISIBLE) View.GONE else View.VISIBLE; render()
        }
        logView = text(tools, "", 12f, color = secondary).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(10), dp(10), dp(10), dp(10))
            background = shape(tonal)
        }
        copyLogButton = button(tools, R.string.classic_copy_log) {
            val body = getString(R.string.region_title) + " " + packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName + "\n" + logLines.joinToString("\n")
            try {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.region_title), body))
                Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show()
            } catch (_: RuntimeException) { Toast.makeText(this, R.string.report_copy_failed, Toast.LENGTH_LONG).show() }
        }
    }
    private fun chooseTheme() {
        val prefs=getSharedPreferences("pixel_appearance",MODE_PRIVATE)
        val values=listOf("system","light","dark")
        val labels=arrayOf(getString(R.string.polish_system),getString(R.string.polish_light),getString(R.string.polish_dark))
        AlertDialog.Builder(this).setTitle(R.string.polish_theme).setSingleChoiceItems(labels,values.indexOf(prefs.getString("theme","system")).coerceAtLeast(0)) { dialog,index ->
            saveDraft();prefs.edit().putString("theme",values[index]).apply();dialog.dismiss();recreate()
        }.setNegativeButton(android.R.string.cancel,null).show()
    }
    private fun chooseColors() {
        val prefs=getSharedPreferences("pixel_appearance",MODE_PRIVATE)
        val options=arrayOf(getString(R.string.pixel_colors_system),getString(R.string.pixel_colors_blue))
        AlertDialog.Builder(this).setTitle(R.string.pixel_colors).setSingleChoiceItems(options,if(prefs.getBoolean("dynamic",true)) 0 else 1) { dialog,index ->
            saveDraft();prefs.edit().putBoolean("dynamic",index==0).apply();dialog.dismiss();recreate()
        }.setNegativeButton(android.R.string.cancel,null).show()
    }
    private fun renderOperationJournal() {
        if(!::journalTitle.isInitialized) return
        OperationJournal.observe(RegionSession.code,RegionSession.busy,RegionSession.autoApnCode,ApnSession.code,ApnSession.pending,ApnSession.busy,ApnSession.target?.plan?.slot,ApnSession.target?.plan?.subId)
        val trace=OperationJournal.latest
        val key=trace?.let { "${it.startedWall}:${it.events.size}:${it.outcome}" } ?: "empty"
        if(key==journalRenderKey) return
        journalRenderKey=key;journalSteps.removeAllViews()
        journalTitle.text=if(trace==null) getString(R.string.polish_last_operation) else getString(R.string.journal_heading,trace.slot+1)
        journalMetadata.visibility=if(trace==null) View.GONE else View.VISIBLE
        journalMetadata.text=trace?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it.startedWall))+" · "+
            (if(it.configMode.isNotEmpty()) "${BundledProfiles.forMode(it.configMode)?.id ?: "a1_by"}.pb · "+getString(if(BundledProfiles.isClear(it.configMode)) R.string.pb_clear else R.string.pb_apply) else if(it.reset) getString(R.string.profile_restore) else "${it.mcc}/${it.mnc}") } ?: ""
        journalDetailsButton.visibility=if(trace==null) View.GONE else View.VISIBLE
        if(trace==null) { journalSummary.setText(R.string.polish_no_operation);return }
        val fullDetails=mutableListOf<String>()
        var previousStage: String? = null
        trace.events.forEach { event ->
            val label=when(event.code) {
                "LOCAL_PB_APPLY" -> getString(R.string.pb_apply)
                "LOCAL_PB_CLEAR" -> getString(R.string.pb_clear)
                "START" -> getString(R.string.polish_stage_start)
                "APPLY" -> getString(R.string.polish_stage_apply)
                "RESET" -> getString(R.string.polish_stage_reset)
                "APN_BACKUP" -> getString(R.string.polish_stage_backup)
                "WAIT" -> getString(R.string.polish_stage_wait)
                "PROFILE_OK" -> getString(R.string.polish_stage_profile_ok)
                "APN_RESTORE" -> getString(R.string.polish_stage_apn)
                "APN_WAIT" -> getString(R.string.apn_pending)
                "SUCCESS" -> getString(R.string.polish_success)
                "FAILED" -> getString(R.string.polish_failed)
                "APN_ATTENTION","APN_UNCONFIRMED" -> getString(R.string.polish_apn_attention)
                "INTERRUPTED" -> getString(R.string.polish_interrupted)
                else -> getString(apnMessage(event.code)).takeIf { event.code.startsWith("APN_") || event.code in setOf("MAPPING_CHANGED","STORE_FAILED") }
                    ?: getString(R.string.polish_stage_error,event.code)
            }
            fullDetails.add(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(event.wall))+" · "+label)
            val shortLabel=when(event.code) {
                "PROFILE_OK" -> getString(R.string.journal_profile_done)
                "APN_ATTENTION","APN_UNCONFIRMED" -> getString(R.string.journal_apn_check)
                "SUCCESS" -> getString(R.string.journal_done)
                else -> if(label.length>75 && event.code.startsWith("APN_")) getString(R.string.journal_apn_check) else label
            }
            if(shortLabel == previousStage) return@forEach
            previousStage = shortLabel
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(0,dp(5),0,dp(5)) }
            journalSteps.addView(row,params(0))
            val successful=event.code in setOf("SUCCESS","PROFILE_OK","APN_VERIFIED","APN_ALREADY_SELECTED")
            val warning=event.code.startsWith("APN_") && event.code !in setOf("APN_BACKUP","APN_RESTORE","APN_WAIT","APN_VERIFIED","APN_ALREADY_SELECTED")
            text(row,if(successful) "✓" else if(warning) "!" else "•",13f,true,
                if(successful) palette.success else if(warning) 0xFFFFB300.toInt() else if(event.code=="FAILED") palette.error else secondary).apply {
                layoutParams=LinearLayout.LayoutParams(dp(15),-2).apply { marginEnd=dp(5) }
            }
            text(row,DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(event.wall)),11f,color=secondary).apply {
                layoutParams=LinearLayout.LayoutParams(dp(68),-2).apply { marginEnd=dp(8) }
            }
            text(row,shortLabel,13f).apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
        }
        val duration=trace.durationMs?.let { if(it<1000) getString(R.string.journal_under_second) else getString(R.string.polish_duration,it/1000.0) }
        val outcome=getString(when(trace.outcome) {
            "SUCCESS" -> R.string.journal_done
            "APN_ATTENTION" -> R.string.journal_profile_apn_check
            "RUNNING" -> R.string.polish_running
            "INTERRUPTED" -> R.string.polish_interrupted
            else -> R.string.polish_failed
        })
        journalSummary.text=listOfNotNull(outcome,duration).joinToString("\n")
        journalSummary.setTextColor(when(trace.outcome) { "SUCCESS" -> palette.success; "APN_ATTENTION" -> 0xFFFFB300.toInt(); "FAILED" -> palette.error; else -> secondary })
        journalDetailsText=fullDetails.joinToString("\n\n")+"\n\n"+getString(R.string.journal_historical)
    }
    private fun showAbout() {
        val manufacturer = Build.MANUFACTURER
        val device = if (Build.MODEL.startsWith(manufacturer, true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
        val version = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "—"
        val appearance = getString(if (palette.dark) R.string.pixel_theme_dark else R.string.pixel_theme_light)
        AlertDialog.Builder(this).setTitle(R.string.pixel_about_device)
            .setMessage(getString(R.string.ui_version, version) + "\n\n" +
                getString(R.string.classic_device, device, Build.VERSION.RELEASE, Build.DISPLAY.ifBlank { Build.ID }) + "\n\n" + appearance)
            .setNeutralButton(R.string.pixel_colors) { _, _ ->
                val preferences = getSharedPreferences("pixel_appearance", MODE_PRIVATE)
                val options = arrayOf(getString(R.string.pixel_colors_system), getString(R.string.pixel_colors_blue))
                AlertDialog.Builder(this).setTitle(R.string.pixel_colors)
                    .setSingleChoiceItems(options, if (preferences.getBoolean("dynamic", true)) 0 else 1) { dialog, which ->
                        preferences.edit().putBoolean("dynamic", which == 0).apply(); dialog.dismiss(); recreate()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }.setPositiveButton(android.R.string.ok, null).show()
    }
    private fun loadDraft() {
        val id=selected()?.subscriptionId ?: -1
        if(editingSub!=id) { pbReadTarget=null;profileValues=null;profileReadError=null;profileValuesAt=0;profileAutoPending=true }
        editingSub=id
    }
    private fun scheduleStartupAccess() {
        handler.removeCallbacks(startupAccess)
        if(visible && !isFinishing && !isDestroyed) handler.post(startupAccess)
    }
    private fun maybeRequestStartupAccess() {
        if(!::localApply.isInitialized || isFinishing || isDestroyed) return
        val phone=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
        when(StartupAccessRules.next(visible,permissionPromptPending,phone,
            drafts().getBoolean("phone_request_seen",false),ReaderSession.accessError(),
            drafts().getBoolean("shizuku_request_seen",false))) {
            StartupAccessRules.Prompt.PHONE -> requestPhone()
            StartupAccessRules.Prompt.SHIZUKU -> authorizeShizuku()
            else -> Unit
        }
    }
    private fun requestPhone() {
        if(permissionPromptPending) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) { reload(); return }
        if (drafts().getBoolean("phone_request_seen", false) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            drafts().edit().putBoolean("phone_request_seen", true).apply()
            permissionPromptPending=true
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 811)
        }
    }
    private fun authorizeShizuku() {
        if(permissionPromptPending) return
        try {
            if (ReaderSession.accessError() == null) Toast.makeText(this, R.string.classic_authorized, Toast.LENGTH_SHORT).show()
            else if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                drafts().edit().putBoolean("shizuku_request_seen",true).apply()
                permissionPromptPending=true
                Shizuku.requestPermission(820)
            }
            else Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_LONG).show()
        } catch (_: RuntimeException) { permissionPromptPending=false;Toast.makeText(this, R.string.shizuku_unavailable, Toast.LENGTH_LONG).show() }
    }
    private fun reload() {
        saveDraft(); subscriptionError = false
        subscriptions = if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) try {
            val sm = getSystemService(SubscriptionManager::class.java)
            if (sm == null) subscriptionError = true
            sm?.activeSubscriptionInfoList.orEmpty().filter { it.simSlotIndex in 0..1 && it.subscriptionId >= 0 }
        } catch (_: RuntimeException) { subscriptionError = true; emptyList() } else emptyList()
        loadDraft(); refreshRadio(); render()
    }
    private fun refreshRadio() {
        readings.clear()
        subscriptions.forEach { sub -> readings[sub.simSlotIndex] = RadioSummary.read(this, sub.subscriptionId) }
        if (::radioTime.isInitialized) radioTime.text = getString(R.string.classic_radio_time, DateFormat.getTimeInstance().format(Date()))
    }
    private fun renderCards() {
        if (!::localApply.isInitialized) return
        val allowed = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        for (slot in 0..1) {
            val sub = subscriptions.singleOrNull { it.simSlotIndex == slot }
            cardName[slot]?.text = sub?.displayName ?: getString(when {
                !allowed -> R.string.classic_permission_missing
                subscriptionError -> R.string.classic_sim_error
                else -> R.string.classic_no_subscription
            })
            val reading = if (sub != null && allowed) readings[slot] else null
            cardCarrierId[slot]?.text = when {
                reading?.carrierId == null -> getString(R.string.home_carrier_id_unknown)
                reading.carrierName != null -> getString(R.string.home_carrier_id_named, reading.carrierId, reading.carrierName)
                else -> getString(R.string.home_carrier_id, reading.carrierId)
            }
            cardNetwork[slot]?.text = getString(R.string.classic_network, reading?.network ?: getString(R.string.classic_unknown))
            cardSignal[slot]?.text = getString(R.string.classic_signal, reading?.dbm?.let { getString(R.string.classic_dbm, it) } ?: getString(R.string.classic_unknown))
            val signalLevel = reading?.level?.coerceIn(0, 4) ?: 0
            val signalColor = when (signalLevel) {
                1 -> 0xFFEF5350.toInt()
                2 -> 0xFFFF9800.toInt()
                3 -> 0xFFFBC02D.toInt()
                4 -> 0xFF4CAF50.toInt()
                else -> secondary
            }
            cardSignal[slot]?.setTextColor(signalColor)
            bars[slot]?.forEachIndexed { index, view -> view.background = shape(if (index < signalLevel) signalColor else outline, Color.TRANSPARENT, 2) }
            cardContainers[slot]?.background = shape(if (slot == selectedSlot) tonal else surface, if (slot == selectedSlot) blue else outline)
            cardLabels[slot]?.text = getString(R.string.classic_slot, slot + 1) + if (slot == selectedSlot) "  ✓" else ""
            cardContainers[slot]?.isSelected = slot == selectedSlot

        }
    }
    private fun checkApi() {
        val sub = selected()
        if (sub == null || ReaderSession.accessError() != null || ReaderSession.state.running) {
            Toast.makeText(this, R.string.classic_not_ready, Toast.LENGTH_LONG).show(); return
        }
        apiTarget = SimRecord(sub.simSlotIndex, sub.subscriptionId); apiSince = System.currentTimeMillis()
        log("API_READ_SIM_${sub.simSlotIndex + 1}")
        ReaderSession.read(this, ReadOperation.TRANSPORT, sub.simSlotIndex, sub.subscriptionId)
        render()
    }
    private fun renderApi() {
        val target = apiTarget
        if (target == null) { apiResult.visibility = View.GONE; return }
        apiResult.visibility = View.VISIBLE
        val value = ReaderSession.state.archive.entries.firstOrNull {
            it.key == ReadResultKey(target.slot, target.subscriptionId, ReadOperation.TRANSPORT)
        }?.value?.takeIf { it.request.startedAt >= apiSince }
        apiResult.text = if (value == null) getString(R.string.classic_api_waiting) else {
            val status = value.result.error?.let { getString(R.string.classic_api_error, it) } ?: getString(when (value.result.transport?.state) {
                0 -> R.string.mmtel_unregistered
                1 -> R.string.mmtel_registering
                2 -> R.string.mmtel_registered
                else -> R.string.unknown
            })
            getString(R.string.classic_api_result, target.slot + 1, DateFormat.getTimeInstance().format(Date(value.finishedAt)), status)
        }
    }
    private fun render() {
        if (!::localApply.isInitialized) return
        val phone = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        phoneBanner.visibility = if (phone) View.GONE else View.VISIBLE
        phoneBanner.setText(R.string.classic_phone_needed)
        val access = ReaderSession.accessError()
        if(::settingsStatus.isInitialized) settingsStatus.text=getString(if(phone) R.string.nav_phone_allowed else R.string.nav_phone_denied) + "\n" +
            getString(when(access) { null -> R.string.classic_authorized; "SHIZUKU_NOT_AUTHORIZED" -> R.string.classic_not_authorized; else -> R.string.classic_shizuku_off })
        shizukuBanner.setText(when (access) {
            null -> R.string.classic_authorized
            "SHIZUKU_NOT_AUTHORIZED" -> R.string.classic_not_authorized
            else -> R.string.classic_shizuku_off
        })
        shizukuBanner.setTextColor(if (access == null) palette.success else palette.error)
        shizukuBanner.background = shape(if (access == null) palette.successSurface else palette.errorSurface)
        renderCards()
        val selectedLabel=getString(R.string.nav_selected_sim,selectedSlot+1,selected()?.displayName ?: getString(R.string.classic_unknown))
        pageSimButtons.forEach { it.text=selectedLabel }
        val sub = selected()
        original.text = getString(R.string.classic_original, sub?.displayName ?: getString(R.string.classic_unknown),
            sub?.mccString ?: getString(R.string.classic_unknown), sub?.mncString ?: getString(R.string.classic_unknown))
        archiveV1.visibility = if (RegionSession.canArchiveV1AfterReboot()) View.VISIBLE else View.GONE
        inspect.visibility = if (RegionSession.blocksReads) View.VISIBLE else View.GONE; inspect.isEnabled = RegionSession.canInspect
        val target = RegionSession.activePlan
        outcome.visibility = if (RegionSession.code == "NOT_STARTED" || RegionSession.code == "NOT_SENT") View.GONE else View.VISIBLE
        resultTarget.text = when {
            target == null -> getString(R.string.region_legacy_result)
            target.configOnly -> getString(R.string.pb_result_target,target.slot+1,getString(if(BundledProfiles.isClear(target.configMode)) R.string.pb_clear else R.string.pb_apply))
            target.reset -> getString(R.string.short_reset_target,target.slot+1,target.operator)
            else -> getString(R.string.short_profile_target,target.slot+1,target.operator,target.mcc,target.mnc)
        }
        result.text = if (target == null && RegionSession.code in RegionGuard.completedApply) getString(R.string.region_legacy_result) else getString(RegionSession.messageResource())
        RegionSession.autoApnCode?.let { result.append("\n\nAPN: " + getString(apnMessage(it))) }
        if (RegionSession.detail.startsWith("native:") || RegionSession.detail.startsWith("native2:")) {
            result.append("\n" + getString(R.string.native_backend))
            if (RegionSession.detail.endsWith("original-identity-fallback")) result.append("\n" + getString(R.string.native_restore_fallback))
            if (RegionSession.code != "MODIFIER_REPORTED_SUCCESS") result.append("\n" + RegionSession.detail)
        }
        val success=RegionSession.code=="MODIFIER_REPORTED_SUCCESS" && target!=null
        resultSummary.text=if(success) getString(if(target?.configOnly==true) R.string.pb_calls_done else if(target?.reset==true) R.string.design_restore_done else R.string.design_apply_done)
            else getString(RegionSession.messageResource())
        val apnOutcome=RegionSession.autoApnCode
        val apnAttention=apnOutcome!=null && apnOutcome !in setOf("APN_ALREADY_SELECTED","APN_VERIFIED")
        if(success && target?.configOnly!=true) resultSummary.append("\n"+getString(when(apnOutcome) {
            "APN_ALREADY_SELECTED" -> R.string.design_apn_selected
            "APN_VERIFIED" -> R.string.design_apn_restored
            null -> R.string.short_apn_unknown
            else -> R.string.short_apn_attention
        }))
        resultSummary.setTextColor(ink)
        if(success) {
            val styled=android.text.SpannableString(resultSummary.text)
            val split=styled.toString().indexOf('\n').takeIf { it>=0 } ?: styled.length
            styled.setSpan(android.text.style.ForegroundColorSpan(palette.success),0,split,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(split<styled.length) styled.setSpan(android.text.style.ForegroundColorSpan(
                if(ProfileDisplayRules.apnVerified(apnOutcome)) palette.success else secondary),split+1,styled.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            resultSummary.text=styled
        }
        resultSummary.visibility=if(success) View.VISIBLE else View.GONE
        result.visibility=if(resultExpanded || !success || apnAttention) View.VISIBLE else View.GONE
        resultMore.visibility=if(success && !apnAttention) View.VISIBLE else View.GONE
        resultMore.setText(if(resultExpanded) R.string.design_hide_details else R.string.design_details)
        val previous = RegionSession.history(selectedSlot)?.takeIf { it.plan.subId == sub?.subscriptionId }
        history.visibility = if(previous==null || !success || target?.slot!=selectedSlot || target?.subId!=sub?.subscriptionId) View.GONE else View.VISIBLE
        if (previous == null) history.setText(R.string.profile_no_history)
        if(previous!=null) history.text=getString(R.string.short_result_time,
            DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(previous.time)),selectedSlot+1)
        apiButton.isEnabled = sub != null && access == null && !ReaderSession.state.running
        if(::settingsAppearance.isInitialized) {
            val pref=getSharedPreferences("pixel_appearance",MODE_PRIVATE)
            val themeName=getString(when(pref.getString("theme","system")) { "light" -> R.string.polish_light; "dark" -> R.string.polish_dark; else -> R.string.polish_system })
            settingsAppearance.text=getString(R.string.polish_theme_value,themeName).replaceFirst(" · ","\n")
            settingsColors.text=getString(R.string.polish_colors_value,getString(if(pref.getBoolean("dynamic",true)) R.string.pixel_colors_system else R.string.pixel_colors_blue)).replaceFirst(" · ","\n")
            phoneSetting.text=getString(R.string.polish_phone_value,getString(if(phone) R.string.polish_allowed else R.string.polish_not_allowed)).replaceFirst(" · ","\n")
            shizukuSetting.text=getString(R.string.polish_shizuku_value,getString(if(access==null) R.string.polish_connected else R.string.polish_not_connected)).replaceFirst(" · ","\n")
            settingsApnLink.text=getString(R.string.settings_working_apn)+"\n"+getString(R.string.nav_selected_sim,selectedSlot+1,selected()?.displayName ?: getString(R.string.classic_no_subscription))
            val language=getSystemService(android.app.LocaleManager::class.java).applicationLocales
            settingsLanguage.text=getString(R.string.language_title)+"\n"+when {
                language.isEmpty -> getString(R.string.polish_system)
                language[0].language=="ru" -> "Русский"
                else -> "English"
            }
            listOf(settingsAppearance,settingsColors,settingsLanguage,phoneSetting,shizukuSetting,settingsApnLink).forEach { styleSettingsValue(it) }
        }
        renderLocalPb()
        renderOperationJournal()
        renderApi()
        renderImsSpoilers()
        renderApn()
        renderProfileOverview()
        renderHomeProfileOffer()
        toolsButton.setText(if (tools.visibility == View.VISIBLE) R.string.classic_less_tests else R.string.classic_more_tests)
        logButton.setText(if (logView.visibility == View.VISIBLE) R.string.journal_log_hide_short else R.string.journal_log_short)
        val eventKey = "${RegionSession.code}:${target?.slot}:${target?.subId}:${target?.mcc}:${target?.mnc}:reset=${target?.reset}"
        if (RegionSession.code != "CHECKING" && eventKey != lastRegionEvent) {
            lastRegionEvent = eventKey; log("REGION_$eventKey")
            val detail=RegionSession.detail
            if(detail.startsWith("native2:") && detail.length<=1200 && detail.matches(Regex("[A-Za-z0-9_:=-]+"))) log("REGION_DETAIL:$detail")
        }
        val apnLogTarget=ApnSession.target
        val apnEvent="${ApnSession.code}:SIM_${apnLogTarget?.plan?.slot?.plus(1) ?: "?"}:subId_${apnLogTarget?.plan?.subId ?: "?"}"
        if (apnEvent != lastApnEvent) { lastApnEvent = apnEvent; log("APN_$apnEvent") }
        logView.text = logLines.joinToString("\n").ifBlank { getString(R.string.classic_log_empty) }
        ApnSession.considerAuto(this,subscriptions.filter { BundledProfiles.find(it.mccString,it.mncString)!=null }.mapNotNull { apnTarget(it) })
        maybeScheduleProfileCheck()
    }
    private fun log(event: String) {
        val time = DateFormat.getTimeInstance().format(Date())
        logLines.addLast("$time · $event")
        while (logLines.size > 60) logLines.removeFirst()
        getSharedPreferences("classic_ui_log", MODE_PRIVATE).edit().putString("lines", logLines.joinToString("\n")).apply()
        if (::logView.isInitialized) logView.text = logLines.joinToString("\n")
    }
    override fun onResume() {
        super.onResume(); visible = true; ApnSession.onForeground(); reload(); RegionSession.onVisible(this);requestProfileCheck();scheduleStartupAccess()
        handler.removeCallbacks(radioTick); handler.postDelayed(radioTick, 5_000)
    }
    override fun onPause() {
        handler.removeCallbacks(startupAccess);ApnSession.onBackground(); visible = false; closeIms(); handler.removeCallbacks(radioTick);handler.removeCallbacks(profileAutoRead);profileAutoQueued=false; saveDraft(); RegionSession.onHidden(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionPromptPending",permissionPromptPending)
        outState.putBoolean("resultExpanded",resultExpanded)
        outState.putInt("page",selectedPage)
        outState.putIntArray("page_scroll",pageScroll.also { if(::pageScrollView.isInitialized) it[selectedPage]=pageScrollView.scrollY })
        outState.putInt("slot", selectedSlot); outState.putBoolean("tools", tools.visibility == View.VISIBLE)
        outState.putBoolean("log", logView.visibility == View.VISIBLE); super.onSaveInstanceState(outState)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 811) {
            permissionPromptPending=false;log("PHONE_PERMISSION_RESULT"); reload();requestProfileCheck();scheduleStartupAccess()
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, R.string.classic_phone_denied, Toast.LENGTH_LONG).show()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacks(startupAccess);handler.removeCallbacks(radioTick);handler.removeCallbacks(profileAutoRead);ReaderSession.remove(listener)
        Shizuku.removeBinderReceivedListener(binderReceived); Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult); super.onDestroy()
    }
}


