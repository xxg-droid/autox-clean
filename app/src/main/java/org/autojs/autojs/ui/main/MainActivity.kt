package org.autojs.autojs.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.aiselp.autojs.codeeditor.EditActivity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.stardust.app.FontScaleUtil
import com.stardust.app.permission.DrawOverlaysPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.external.foreground.ForegroundService
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.ui.build.ProjectConfigActivity
import org.autojs.autojs.ui.build.ProjectConfigActivity_
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.compose.theme.AutoXJsTheme
import org.autojs.autojs.ui.compose.theme.MintGreen
import org.autojs.autojs.ui.compose.theme.MintGreenDark
import org.autojs.autojs.ui.compose.theme.MintGreenLight
import org.autojs.autojs.ui.compose.widget.MyIcon
import org.autojs.autojs.ui.compose.widget.SearchBox2
import org.autojs.autojs.ui.explorer.ExplorerViewKt
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.components.LogButton
import org.autojs.autojs.ui.main.drawer.DrawerPage
import org.autojs.autojs.ui.main.scripts.ScriptListFragment
import org.autojs.autojs.ui.main.task.TaskManagerFragmentKt
import org.autojs.autojs.ui.widget.fillMaxSize
import org.autojs.autoxjs.R

data class BottomNavigationItem(val icon: Int, val label: String)

class MainActivity : FragmentActivity() {

    companion object {
        @JvmStatic
        fun getIntent(context: Context) = Intent(context, MainActivity::class.java)
    }

    // 全 App 字号放大一档（fontScale 1.2），见 FontScaleUtil。
    // MainActivity 不继承 BaseActivity，需单独包一层；绝对值赋值，不会叠加。
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontScaleUtil.wrap(newBase))
    }

    private val scriptListFragment by lazy { ScriptListFragment() }
    private val taskManagerFragment by lazy { TaskManagerFragmentKt() }
    private var lastBackPressedTime = 0L
    private var drawerState: DrawerState? = null
    private val viewPager: ViewPager2 by lazy { ViewPager2(this) }
    private var scope: CoroutineScope? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Pref.isForegroundServiceEnabled()) ForegroundService.start(this)
        else ForegroundService.stop(this)

        if (Pref.isFloatingMenuShown() && !FloatyWindowManger.isCircularMenuShowing()) {
            if (DrawOverlaysPermission.isCanDrawOverlays(this)) FloatyWindowManger.showCircularMenu()
            else Pref.setFloatingMenuShown(false)
        }
        setContent {
            scope = rememberCoroutineScope()
            AutoXJsTheme {
                Surface(color = MaterialTheme.colors.background) {
                    val permission = rememberExternalStoragePermissionsState {
                        if (it) {
                            scriptListFragment.explorerView.onRefresh()
                        }
                    }
                    LaunchedEffect(key1 = Unit, block = {
                        permission.launchMultiplePermissionRequest()
                    })
                    MainPage(
                        activity = this,
                        scriptListFragment = scriptListFragment,
                        taskManagerFragment = taskManagerFragment,
                        onDrawerState = {
                            this.drawerState = it
                        },
                        viewPager = viewPager
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TimedTaskScheduler.ensureCheckTaskWorks(application)
    }

    override fun onBackPressed() {
        if (drawerState?.isOpen == true) {
            scope?.launch { drawerState?.close() }
            return
        }
        if (viewPager.currentItem == 0 && scriptListFragment.onBackPressed()) {
            return
        }
        back()
    }

    private fun back() {
        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastBackPressedTime
        if (interval > 2000) {
            lastBackPressedTime = currentTime
            Toast.makeText(
                this,
                getString(R.string.text_press_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
        } else super.onBackPressed()
    }
}

@Composable
fun MainPage(
    activity: FragmentActivity,
    scriptListFragment: ScriptListFragment,
    taskManagerFragment: TaskManagerFragmentKt,
    onDrawerState: (DrawerState) -> Unit,
    viewPager: ViewPager2
) {
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    onDrawerState(scaffoldState.drawerState)
    val scope = rememberCoroutineScope()

    val bottomBarItems = remember {
        getBottomItems(context)
    }
    var currentPage by remember {
        mutableStateOf(0)
    }
    // build27 UI 改版：右下角 FAB 打开的新建菜单（新建目录/文件/导入/新建项目）
    var fabMenuExpanded by remember {
        mutableStateOf(false)
    }

    SetSystemUI(scaffoldState)

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        scaffoldState = scaffoldState,
        drawerGesturesEnabled = scaffoldState.drawerState.isOpen,
        floatingActionButton = {
            // 只在首页显示；管理页不显示
            if (currentPage == 0) {
                Box {
                    FloatingActionButton(
                        onClick = { fabMenuExpanded = true },
                        backgroundColor = MintGreen,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(width = 96.dp, height = 56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(id = R.string.desc_more),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    TopAppBarMenu(
                        expanded = fabMenuExpanded,
                        onDismissRequest = { fabMenuExpanded = false },
                        scriptListFragment = scriptListFragment
                    )
                }
            }
        },
        topBar = {
            Surface(elevation = 4.dp, color = MaterialTheme.colors.primarySurface) {
                Column() {
                    Spacer(
                        modifier = Modifier
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                    )
                    TopBar(
                        currentPage = currentPage,
                        requestOpenDrawer = {
                            scope.launch { scaffoldState.drawerState.open() }
                        },
                        onSearch = { keyword ->
                            scriptListFragment.explorerView.setFilter { it.name.contains(keyword) }
                        },
                        scriptListFragment = scriptListFragment,
                    )
                }
            }
        },
        bottomBar = {
            Surface(elevation = 4.dp, color = MaterialTheme.colors.surface) {
                Column {
                    BottomBar(bottomBarItems, currentPage, onSelectedChange = { currentPage = it })
                    Spacer(
                        modifier = Modifier
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    )
                }
            }
        },
        drawerContent = {
            DrawerPage()
        },

        ) {
        AndroidView(
            modifier = Modifier.padding(it),
            factory = {
                viewPager.apply {
                    fillMaxSize()
                    adapter = ViewPager2Adapter(
                        activity,
                        scriptListFragment,
                        taskManagerFragment
                    )
                    isUserInputEnabled = false
                    ViewCompat.setNestedScrollingEnabled(this, true)
                }
            },
            update = { viewPager0 ->
                viewPager0.currentItem = currentPage
            }
        )
    }
}

fun showExternalStoragePermissionToast(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.text_please_enable_external_storage),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * 外部存储权限请求状态。
 *
 * Android 11+（SDK >= 30）上 READ/WRITE_EXTERNAL_STORAGE 不再授予 /sdcard
 * 脚本目录的广泛访问权，且 targetSdk 33 时 Manifest 里的
 * requestLegacyExternalStorage 已被系统忽略，必须持有“所有文件访问权限”
 *（MANAGE_EXTERNAL_STORAGE）才能读写脚本文件。因此 SDK >= 30 时改为引导
 * 用户去系统设置开启所有文件访问权限，返回后重新检查
 * Environment.isExternalStorageManager()；低版本保留原来的
 * READ/WRITE 运行时权限申请流程。调用点（onCreate、新建目录/文件、导入）
 * 不用改，仍调用 launchMultiplePermissionRequest()。
 */
interface ExternalStoragePermissionState {
    fun launchMultiplePermissionRequest()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberExternalStoragePermissionsState(onPermissionsResult: (allAllow: Boolean) -> Unit): ExternalStoragePermissionState {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onPermissionsResult)
    val legacyState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
        onPermissionsResult = { map ->
            latestCallback(map.all { it.value })
        })
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        latestCallback(Environment.isExternalStorageManager())
    }
    return remember(legacyState, allFilesAccessLauncher) {
        object : ExternalStoragePermissionState {
            override fun launchMultiplePermissionRequest() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        latestCallback(true)
                        return
                    }
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    try {
                        allFilesAccessLauncher.launch(intent)
                    } catch (e: Exception) {
                        // 极少数系统无带包名的单应用页时，退回通用“所有文件访问权限”列表页
                        allFilesAccessLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                } else {
                    legacyState.launchMultiplePermissionRequest()
                }
            }
        }
    }
}

@Composable
private fun SetSystemUI(scaffoldState: ScaffoldState) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons =
        if (MaterialTheme.colors.isLight) {
            scaffoldState.drawerState.isOpen || scaffoldState.drawerState.isAnimationRunning
        } else false

    val navigationUseDarkIcons = MaterialTheme.colors.isLight
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons
        )
        systemUiController.setNavigationBarColor(
            Color.Transparent,
            darkIcons = navigationUseDarkIcons
        )
    }
}

private fun getBottomItems(context: Context) = mutableStateListOf(
    BottomNavigationItem(
        R.drawable.ic_home,
        context.getString(R.string.text_home)
    ),
    BottomNavigationItem(
        R.drawable.ic_manage,
        context.getString(R.string.text_management)
    )
)

// build27 UI 改版：Material You 底部导航——选中图标背后有薄荷绿椭圆胶囊，
// 未选中变淡，栏高加到 80dp；保留 首页 / 管理 两项。
@Composable
fun BottomBar(
    items: List<BottomNavigationItem>,
    currentSelected: Int,
    onSelectedChange: (Int) -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val pillColor = if (isLight) MintGreenLight else MintGreen
    val selectedContentColor = if (isLight) MintGreenDark else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = currentSelected == index
            val contentColor = if (selected) selectedContentColor else Color.Gray
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { if (!selected) onSelectedChange(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) pillColor else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.label,
                    color = contentColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    currentPage: Int,
    requestOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    scriptListFragment: ScriptListFragment,
) {
    var isSearch by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    TopAppBar(elevation = 0.dp) {
        CompositionLocalProvider(
            LocalContentAlpha provides ContentAlpha.high,
        ) {
            if (!isSearch) {
                IconButton(onClick = requestOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(id = R.string.text_menu),
                    )
                }

                ProvideTextStyle(value = MaterialTheme.typography.h6) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.app_name)
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, EditActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "editor"
                        )
                    }
                }
                IconButton(onClick = { isSearch = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.text_search)
                    )
                }
            } else {
                IconButton(onClick = { isSearch = false }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.text_exit_search)
                    )
                }

                var keyword by remember {
                    mutableStateOf("")
                }
                SearchBox2(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = stringResource(id = R.string.text_search)) },
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch(keyword)
                    })
                )
            }
            LogButton()
            // build27 UI 改版：顶栏的“+”已移到右下角 FAB（首页），这里只留管理页的停止按钮
            if (currentPage == 1) {
                IconButton(onClick = { AutoJs.getInstance().scriptEngineService.stopAll() }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(id = R.string.desc_more)
                    )
                }
            }

        }
    }
}

@Composable
fun TopAppBarMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    scriptListFragment: ScriptListFragment
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, offset = offset) {
        val context = LocalContext.current
        NewDirectory(context, scriptListFragment, onDismissRequest)
        NewFile(context, scriptListFragment, onDismissRequest)
        ImportFile(context, scriptListFragment, onDismissRequest)
        NewProject(context, scriptListFragment, onDismissRequest)
//        DropdownMenuItem(onClick = { /*TODO*/ }) {
//            MyIcon(
//                painter = painterResource(id = R.drawable.ic_timed_task),
//                contentDescription = stringResource(id = R.string.text_switch_timed_task_scheduler)
//            )
//            Spacer(modifier = Modifier.width(8.dp))
//            Text(text = stringResource(id = R.string.text_switch_timed_task_scheduler))
//        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NewDirectory(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).newDirectory()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_dir),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_directory))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NewFile(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).newFile()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_file),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_file))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ImportFile(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).importFile()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_open),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_import))
    }
}

@Composable
private fun NewProject(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    DropdownMenuItem(onClick = {
        onDismissRequest()
        ProjectConfigActivity_.intent(context)
            .extra(
                ProjectConfigActivity.EXTRA_PARENT_DIRECTORY,
                scriptListFragment.explorerView.currentPage?.path
            )
            .extra(ProjectConfigActivity.EXTRA_NEW_PROJECT, true)
            .start()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_project2),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_project))
    }
}

private fun getScriptOperations(
    context: Context,
    explorerView: ExplorerViewKt
): ScriptOperations {
    return ScriptOperations(
        context,
        explorerView,
        explorerView.currentPage
    )
}