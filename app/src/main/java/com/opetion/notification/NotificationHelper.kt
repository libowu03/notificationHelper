package com.opetion.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleObserver
import java.lang.reflect.Field
import java.lang.reflect.Method


/**
 * @author:libowu
 * @Date:2021/5/28
 * @Description:通知栏工具类
 */
class NotificationHelper {

    companion object{

        /**
         * 取消通知id为notificationId的通知
         */
        fun cancel(notificationId:Int,activity: Context,tag:String?=null){
            val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            tag?.let {
                manager.cancel(tag,notificationId)
            }?:let {
                manager.cancel(notificationId)
            }
        }

        /**
         * 取消所有通知消息
         */
        fun cancelAll(activity: Context){
            val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }

        /**
         * 检测Id为channelId的通知渠道是否存在横幅通知的权限
         * @param channelId 通知渠道的id
         */
        fun hadImportancePermission(activity: Context,channelId:String): Boolean {
            val mNotificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = mNotificationManager.getNotificationChannel(channelId)
                //是否开启了通知权限，默认的channel是开启通知权限的，除非用户手动关闭。
                val enable = mNotificationManager.areNotificationsEnabled()
                if (!enable) {
                    return false
                }
                if (channel.importance == 0) {
                    return false
                }
                //重要程度大于HIGH的通知将会以横幅通知的形式显示，如果高于或等于这个值，说明存在横幅通知的权限
                return channel.importance >= NotificationManager.IMPORTANCE_HIGH
            } else {
                return NotificationManagerCompat.from(activity).areNotificationsEnabled()
            }
        }

        /**
         * 检测是否存在消息通知权限
         * @param channelId 通知渠道id，允许为空，如果为空，则是获取app的通知权限
         */
        fun hadNotificationPermission(activity: Context,channelId:String?=null):Boolean{
            val mNotificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
                val channel = mNotificationManager.getNotificationChannel(channelId)
                //是否开启了通知权限，默认的channel是开启通知权限的，除非用户手动关闭。
                val enable = mNotificationManager.areNotificationsEnabled()
                if (!enable) {
                    return false
                }
                if (channel == null){
                    return false
                }
                return channel.importance > NotificationManager.IMPORTANCE_NONE
            } else {
                return NotificationManagerCompat.from(activity).areNotificationsEnabled()
            }
        }

        /**
         * 跳转到权限设置界面,如果存在channel，则进入具体通知channel的设置界面
         */
        fun sendToSetting(context: Context,channelId:String?=null) {
            try {
                val intent = Intent()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    channelId?.let {
                        intent.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                        intent.putExtra(
                            Settings.EXTRA_CHANNEL_ID,
                            channelId
                        )
                    }?:let {
                        when {
                            SystemUtil.isHuaWei() -> {
                                intent.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                                intent.putExtra(Settings.EXTRA_CHANNEL_ID, "huawei")
                            }
                            else -> {
                                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            }
                        }
                    }
                    context.startActivity(intent)
                } else if (Build.VERSION.SDK_INT >= 21) {
                    intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    intent.putExtra("app_package", context.packageName)
                    intent.putExtra("app_uid", context.applicationInfo.uid);
                    context.startActivity(intent)
                } else {//其它
                    intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.i("日志","跳转失败")
            }
        }
    }

    /**
     * 通知的具体功能实现类
     * @param channelName 8.0系统以上channel的名称
     * @param notificationId 通知的id，可以通过该id取消通知显示
     * @param importance 该通知渠道的消息重要等级
     * @param enableSeveralChannelExist 允许设置中多个同名的channel存在（不建议开启） true：允许 false：不允许 默认关闭
     */
    class NotificationHelperBuilder(
        var activity: Context,
        var channelName: String,
        var channelId:String,
        var notificationId: Int,
        var importance: Priority = Priority.DEFAULT,
        var enableSeveralChannelExist:Boolean = false
    ): LifecycleObserver {
        private var manager: NotificationCompat.Builder
        private var channel: NotificationChannel? = null
        private var enableLight: Boolean = false
        private var notification:Notification?=null
        private var summaryId:Int = -1
        private var summaryArray:MutableList<Notification>?=null
        private var customMessageCount:Int=0

        init {
            createNotificationChannel(channelName,channelId,importance)
            if (enableSeveralChannelExist){
                manager = NotificationCompat.Builder(activity, "${hashCode()}")
            }else{
                manager = NotificationCompat.Builder(activity, channelId)
            }
            manager.setAutoCancel(true)
        }

        /**
         * 安卓8.0以上穿件通知channel
         * @param notificationChannelName 8.0系统以上通知channel的名称
         * @param importance 通知重要等级
         * NONE 不重要的消息，任意情况下都不显示
         * MIN 不重要要的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
         * LOW 重要程度较低的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
         * DEFAULT 默认的重要程度,状态栏有图标，有声音提示
         * HIGH 重要程度比较高的消息,状态栏有图标，有声音提示，存在震动提示
         * MAX 非常重要的消息提示,状态栏有图标，有声音提示，存在震动提示
         */
        @SuppressLint("WrongConstant")
        private fun createNotificationChannel(
            notificationChannelName: String,
            channelId:String,
            importance: Priority = Priority.DEFAULT
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = notificationChannelName
                if (enableSeveralChannelExist){
                    channel = NotificationChannel("${hashCode()}", name, getImportance(importance))
                }else{
                    channel = NotificationChannel(channelId, name, getImportance(importance))
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        private fun getImportance(importance: Priority):Int{
            return when (importance) {
                Priority.NONE -> NotificationManager.IMPORTANCE_NONE
                Priority.MIN -> NotificationManager.IMPORTANCE_MIN
                Priority.LOW -> NotificationManager.IMPORTANCE_LOW
                Priority.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
                Priority.HIGH -> NotificationManager.IMPORTANCE_HIGH
                Priority.MAX -> NotificationManager.IMPORTANCE_MAX
            }
        }

        /**
         * 展示通知
         */
        fun show():Notification? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager: NotificationManager =
                    activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                channel?.let {
                    notificationManager.createNotificationChannel(it)
                }
            }
            with(NotificationManagerCompat.from(activity)) {
                notification = manager.build()
                if (customMessageCount > 0){
                    addMessageCount(customMessageCount)
                }
                notification?.let {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        it.flags = Notification.FLAG_SHOW_LIGHTS
                    }
                    summaryArray?.let {
                        for (item in it.withIndex()){
                            notify(item.index,item.value)
                        }
                    }
                    notify(notificationId, it)
                }
            }
            return notification
        }


        /**
         * 展示通知
         * @param notification 需要展示的通知消息
         */
        fun show(notification:Notification?){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager: NotificationManager =
                    activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                channel?.let {
                    notificationManager.createNotificationChannel(it)
                }
            }
            with(NotificationManagerCompat.from(activity)) {
                notification?.let {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        it.flags = Notification.FLAG_SHOW_LIGHTS
                    }
                    notify(notificationId, it)
                }
            }
        }

        private fun addMessageCount(messageNumber: Int){
            if (SystemUtil.isHuaWei()){
                try {
                    val bunlde = Bundle()
                    bunlde.putString(
                        "package",
                        activity.packageName
                    )
                    bunlde.putString(
                        "class",
                        "${activity::class.java.name}"
                    )
                    Log.i("日志","包名：${activity::class.java.name}")
                    bunlde.putInt("badgenumber", messageNumber)
                    activity.contentResolver.call(
                        Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
                        "change_badge",
                        null,
                        bunlde
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.i("日志","错误：${e.message}")
                }
            }else if (SystemUtil.isXiaomi()){
                val field: Field = notification!!::class.java.getDeclaredField("extraNotification")
                val extraNotification: Any = field.get(notification!!)
                val method: Method = extraNotification::class.java.getDeclaredMethod(
                    "setMessageCount",
                    Int::class.javaPrimitiveType
                )
                method.invoke(extraNotification, messageNumber)
            }
        }

        /**
         * 只生成notification但是不展示
         */
        fun build():Notification?{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager: NotificationManager =
                    activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                channel?.let {
                    notificationManager.createNotificationChannel(it)
                }
            }
            notification = manager.build()
            notification?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    it.flags = Notification.FLAG_SHOW_LIGHTS
                }
            }
            return notification
        }

        /**
         * 获取通知
         */
        fun getNotification():Notification?{
            return notification
        }

        /**
         * @param icon 通知栏显示的小图标
         * @param level 如果你的icon是LevelListDrawable，则可以通过设置层级来控制显示哪张图片。比如以下代码，如果设置level为10，则显示lamp_off这张图片。
         * <?xml version="1.0" encoding="utf-8"?>
         *  <level-list xmlns:android="http://schemas.android.com/apk/res/android">
         *      <item android:drawable="@drawable/lamp_on"
         *            android:minLevel="12"
         *            android:maxLevel="20"/>
         *      <item android:drawable="@drawable/lamp_off"
         *            android:minLevel="6"
         *            android:maxLevel="10"/>
         * </level-list>
         */
        fun setSmallIcon(icon: Int, level: Int = 0): NotificationHelperBuilder {
            manager.setSmallIcon(icon, level)
            return this
        }

        /**
         * 设置通知与锁屏intent的关联，解锁后将触发pendingIntent这个动作。比如想用户解锁后进入一个自己app的锁屏界面，就可以使用改方法设置了。
         * 不过过于影响用户体验，不建议使用，看需求吧。
         * 安卓10（SDK29）及以上的系统需要在AndroidManifest.xml中添加权限USE_FULL_SCREEN_INTENT
         */
        fun setFullScreenIntent(pendingIntent: PendingIntent,highPriority:Boolean):NotificationHelperBuilder{
            manager.setFullScreenIntent(pendingIntent,highPriority)
            return this
        }

        /**
         * 设置通知过期时间，即从显示开始计时，显示通知后。到timeAfter这段时间之后，通知将会自动取消
         * @param timeAfter 等待消失时间
         */
        fun setTimeAfter(timeAfter:Long):NotificationHelperBuilder{
            manager.setTimeoutAfter(timeAfter)
            return this
        }

        /**
         * 设置大图
         * @param resourceId 图片资源id
         * @param isThumbnail 是否是缩略图，如果是，则图片以缩略图形式显示在通知旁边，不是则显示大图
         * @param isOnlyShowThumbnailInCollapsed 是否只在折叠通知栏情况下展示缩略图
         * true：折叠情况下显示缩略图 false：不显示缩略图。该项只在isThumbnail为false时设置有效
         */
        fun setLargeIcon(
            resourceId: Int,
            isThumbnail: Boolean = true,
            isOnlyShowThumbnailInCollapsed: Boolean = false
        ): NotificationHelperBuilder {
            val bitmap = BitmapFactory.decodeResource(activity.resources, resourceId)
            bitmap?.let {
                if (isThumbnail) {
                    manager.setLargeIcon(it)
                } else {
                    if (isOnlyShowThumbnailInCollapsed){
                        manager.setStyle(
                            NotificationCompat.BigPictureStyle().bigPicture(it)
                        )
                    }else{
                        manager.setLargeIcon(it)
                        manager.setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(it).bigLargeIcon(null)
                        )
                    }
                }
            }
            return this
        }

        /**
         * 设置通知的分组信息，groupKey下的所有通知都将会显示在一组通知里面
         */
        fun setGround(groupKey:String):NotificationHelperBuilder{
            manager.setGroup(groupKey)
            return this
        }

        /**
         * 是否开启分组摘要
         * @param enable 是否开启分组摘要
         * @param summaryId 摘要的id
         */
        fun setGroupSummary(enable:Boolean,summaryId:Int):NotificationHelperBuilder{
            manager.setGroupSummary(enable)
            this.summaryId = summaryId
            return this
        }

        /**
         * 在分组里面添加多个摘要通知
         * @param notification 摘要通知
         */
        fun addSummary(notification:Notification?):NotificationHelperBuilder{
            if (summaryArray == null){
                summaryArray = mutableListOf()
            }
            notification?.let {
                summaryArray?.add(notification)
            }
            return this
        }

        /**
         * 设置大图
         * @param largeBitmap 将要使用的大图
         * @param isThumbnail 是否是缩略图，如果是，则图片以缩略图形式显示在通知旁边，不是则显示大图
         * @param isOnlyShowThumbnailInCollapsed 是否只在折叠通知栏情况下展示缩略图
         * true：折叠情况下显示缩略图 false：不显示缩略图。该项只在isThumbnail为false时设置有效
         */
        fun setLargeIcon(
            largeBitmap: Bitmap?,
            isThumbnail: Boolean = true,
            isOnlyShowThumbnailInCollapsed: Boolean = false
        ): NotificationHelperBuilder {
            largeBitmap?.let {
                if (isThumbnail) {
                    manager.setLargeIcon(it)
                } else {
                    if (isOnlyShowThumbnailInCollapsed){
                        manager.setStyle(
                            NotificationCompat.BigPictureStyle().bigPicture(it)
                        )
                    }else{
                        manager.setLargeIcon(it)
                        manager.setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(it).bigLargeIcon(null)
                        )
                    }
                }
            }
            return this
        }

        /**
         * 设置通知的标题
         * @param title 标题
         */
        fun setContentTitle(title: CharSequence?): NotificationHelperBuilder {
            manager.setContentTitle(title ?: "")
            return this
        }

        fun setSubtext(subtext:CharSequence){
            manager.setSubText(subtext)
        }

        /**
         * 设置通知的具体内容
         * @param content 正文内容
         * @param enableSingle 是否单行显示
         */
        fun setContentText(
            content: CharSequence?,
            enableSingle: Boolean = true
        ): NotificationHelperBuilder {
            if (enableSingle) {
                manager.setContentText(content ?: "")
            } else {
                manager.setStyle(NotificationCompat.BigTextStyle().bigText(content))
            }
            return this
        }

        /**
         * 设置通知的具体内容
         * @param contentResourceId 正文内容文本资源id
         * @param enableSingle 是否单行显示
         */
        fun setContentText(
            contentResourceId: Int,
            enableSingle: Boolean = true
        ): NotificationHelperBuilder {
            if (enableSingle) {
                manager.setContentText(activity.resources.getString(contentResourceId) ?: "")
            } else {
                manager.setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        activity.resources.getString(
                            contentResourceId
                        )
                    )
                )
            }
            return this
        }

        /**
         * 设置文本样式，比如
         * NotificationCompat.BigTextStyle()、NotificationCompat.InboxStyle()、NotificationCompat.MessagingStyle()、NotificationCompat.MediaStyle()等
         * @param contentStyle 样式内容
         */
        fun setContentStyle(contentStyle: NotificationCompat.Style){
            manager.setStyle(contentStyle)
        }

        /**
         * 添加通知中按钮的点击事件，只对添加了MediaStyle的有效
         * @param btnResourceId 按钮图片资源id
         * @param text 按钮文本
         * @param pendingIntent 点击触发事件
         */
        fun addAction(btnResourceId:Int,text:String,pendingIntent: PendingIntent){
            manager.addAction(btnResourceId,text,pendingIntent)
        }


        /**
         * 设置点击后通知是否自动消失
         * @param isAutoCancel true:点击后通知自动消失 false:点击通知后消息仍然保留
         */
        fun setAutoCancel(isAutoCancel: Boolean): NotificationHelperBuilder {
            manager.setAutoCancel(isAutoCancel)
            return this
        }

        /**
         * 该方法可以设置通知的声音，震动，灯光。可以使用组合的方式输入
         * 输入范围：Notification.DEFAULT_SOUND, Notification.DEFAULT_VIBRATE,Notification.DEFAULT_LIGHTS,Notification.DEFAULT_ALL
         * Notification.DEFAULT_SOUND：添加默认的通知声音
         * Notification.DEFAULT_VIBRATE：添加默认震动
         * Notification.DEFAULT_LIGHTS：添加默认的呼吸灯提示
         * Notification.DEFAULT_ALL：声音，震动，灯光均采用默认
         * @param defaultsValue 默认值
         */
        fun setDefaults(defaultsValue: Int): NotificationHelperBuilder {
            manager.setDefaults(defaultsValue)
            return this
        }

        /**
         * 通知上是否显示通知时间(true：通知顶部会出现通知展示的时间  false：不显示通知展示时间)
         * @param isShow
         */
        fun setShowWhen(isShow: Boolean): NotificationHelperBuilder {
            manager.setShowWhen(isShow)
            return this
        }

        /**
         * 设置通知时间，比如设置为System.currentTimeMillis()+1800000,通知顶部将会显示3分钟后。当然，这个值最终也是会变的，比如3分钟后，这个值可能就变成刚刚了。
         * @param showDate 通知将要显示的时间
         */
        fun setWhen(showDate: Long): NotificationHelperBuilder {
            setShowWhen(true)
            manager.setWhen(showDate)
            return this
        }

        /**
         * 设置可见状态，默认值是Notification.VISIBILITY_PRIVATE
         * 可选的值有
         * VISIBILITY_PRIVATE：显示基本信息，例如通知图标和内容标题，但隐藏通知的完整内容。
         * VISIBILITY_PUBLIC： 显示通知的完整内容。
         * VISIBILITY_SECRET：不在锁定屏幕上显示该通知的任何部分。
         */
        fun setVisibility(visibility:Visibility):NotificationHelperBuilder{
            when(visibility){
                Visibility.PRIVATE -> {
                    manager.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                }
                Visibility.PUBLIC -> {
                    manager.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                }
                Visibility.SECRET -> {
                    manager.setVisibility(NotificationCompat.VISIBILITY_SECRET)
                }
            }
            return this
        }

        /**
         * 通知所属的分类
         * @param category 分类id
         * 允许输入的参数有：{@link android.app.Notification.CATEGORY_MESSAGE},CATEGORY_开头的都是允许输入的参数
         *
         */
        fun setCategory(category: String): NotificationHelperBuilder {
            manager.setCategory(category)
            return this
        }

        /**
         * 设置点击事件
         */
        fun setContentIntent(pendingIntent: PendingIntent): NotificationHelperBuilder {
            manager.setContentIntent(pendingIntent)
            return this
        }

        /**
         * 通知被清除时将会触发该通知
         * @param deleteIntent 通知删除时触发的事件
         */
        fun setDeleteIntent(deleteIntent:PendingIntent):NotificationHelperBuilder{
            manager.setDeleteIntent(deleteIntent)
            return this
        }

        /**
         * 设置自定义通知布局
         * @param customViewLayoutId 未展开时候的通知布局 传入View.NO_ID表示不需要布局
         * @param customBigViewLayoutId 展开后的通知布局 传入View.NO_ID表示不需要布局
         * @param enableSystemStyle 是否启用标准通知图标和标题装饰通知，这里建议打开，如果不使用系统默认的标准通知装饰，可能存在布局兼容问题
         * 注意：父布局，也就是customViewLayoutId，customBigViewLayoutId这两个布局的最外层只能是LinearLayout,RelativeLayout,FrameLayout等这些简单的布局,如果设置ConstraintLayout通知将不会显示
         */
        fun setCustomView(customViewLayoutId:Int,customBigViewLayoutId:Int,enableSystemStyle:Boolean = true):NotificationHelperBuilder{
            if (enableSystemStyle){
                manager.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }
            if (notificationId != View.NO_ID){
                val notificationLayout = RemoteViews(activity.packageName, customViewLayoutId)
                manager.setCustomContentView(notificationLayout)
            }
            if (customBigViewLayoutId != View.NO_ID){
                val notificationLayoutExpanded = RemoteViews(activity.packageName, customBigViewLayoutId)
                manager.setCustomBigContentView(notificationLayoutExpanded)
            }
            return this
        }

        /**
         * 设置自定义通知布局
         * @param customViewLayoutView 未展开时候的通知布局 传入View.NO_ID表示不需要布局
         * @param customBigViewLayoutView 展开后的通知布局 传入View.NO_ID表示不需要布局
         * @param enableSystemStyle 是否启用标准通知图标和标题装饰通知，这里建议打开，如果不使用系统默认的标准通知装饰，可能存在布局兼容问题
         * 注意：父布局，也就是customViewLayoutId，customBigViewLayoutId这两个布局的最外层只能是LinearLayout,RelativeLayout,FrameLayout等这些简单的布局,如果设置ConstraintLayout通知将不会显示
         */
        fun setCustomView(customViewLayoutView:RemoteViews?,customBigViewLayoutView:RemoteViews?,enableSystemStyle:Boolean = true):NotificationHelperBuilder{
            if (enableSystemStyle){
                manager.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }
            customViewLayoutView?.let {
                manager.setCustomContentView(it)
            }
            customBigViewLayoutView?.let {
                manager.setCustomBigContentView(it)
            }
            return this
        }

        /**
         * 是否在启动器的app入口图标上显示通知圆点
         */
        fun setShowBadge(enableShowBadge:Boolean):NotificationHelperBuilder{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channel?.setShowBadge(enableShowBadge)
            }
            return this
        }

        /**
         * 这个可以设置启动器app图标入口通知圆点上显示的信息数量
         * @param messageNumber 想要通知圆点显示的信息条数
         * 注意：谷歌手机上的app图标小红点是不会显示消息数量的，只有在长按app图标显示的消息列表顶部才会显示出设置的消息数量
         */
        fun setNumber(messageNumber:Int):NotificationHelperBuilder{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channel?.setShowBadge(true)
            }
            customMessageCount = messageNumber
            manager.setNumber(messageNumber)
            return this
        }


        /**
         * 长按app图标时显示与通知关联的大图标或小图标，默认情况下显示大图标
         * 估计也就谷歌等比较原生的系统有效了，华为上没起作用
         */
        fun setBadgeIconType(icon:Int):NotificationHelperBuilder{
            manager.setBadgeIconType(icon)
            return this
        }

        /**
         * 通知到达时是否允许震动(是否有震动取决于通知所属的channel是否开启了正东，如果在第一次穿件通知channel时是关闭震动的，则调用次方法将无效)
         * @param isEnable true：允许震动 false：不允许震动
         */
        fun enableVibration(isEnable: Boolean):NotificationHelperBuilder {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                channel?.enableVibration(isEnable)
            }
            return this
        }

        /**
         * 设置震动的方式
         * 比如设置数据为：[0L,300L,500L,700L],则表示延迟0ms，然后振动300ms，在延迟500ms， 接着再振动700ms
         */
        fun setVibrate(vibrateWay: LongArray) {
            manager.setVibrate(vibrateWay)
        }

        /**
         * 设置呼吸灯闪烁方式
         * @param argb 呼吸灯的颜色，可能不是所有设备都可以显示改颜色，看手机配置了
         * @param onDuration 亮灯持续时间
         * @param offDuration 暗等持续时间
         */
        fun setLight(argb: Int, onDuration: Int, offDuration: Int, isEnableLight: Boolean = true) {
            manager.setLights(argb, onDuration, offDuration)
            enableLight(isEnableLight)
        }

        /**
         * 如果设备存在呼吸灯，是否允许通知到达时开启呼吸灯通知
         */
        fun enableLight(isEnable: Boolean) {
            enableLight = isEnable
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                channel?.enableLights(isEnable)
            }
        }

        /**
         * 设置等级（8.0及以上系统需要在穿件channel时就设置好通知等级了）
         * @param priority 通知重要等级
         * NONE 不重要的消息，任意情况下都不显示(8.0一下系统没有该项)
         * MIN 不重要要的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
         * LOW 重要程度较低的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
         * DEFAULT 默认的重要程度,状态栏有图标，有声音提示
         * HIGH 重要程度比较高的消息,状态栏有图标，有声音提示，存在震动提示
         * MAX 非常重要的消息提示,状态栏有图标，有声音提示，存在震动提示
         */
        fun setPriority(priority: Priority): NotificationHelperBuilder {
            when (priority) {
                Priority.MIN, Priority.NONE -> {
                    manager.priority = NotificationCompat.PRIORITY_MIN
                }
                Priority.LOW -> {
                    manager.priority = NotificationCompat.PRIORITY_LOW
                }
                Priority.DEFAULT -> {
                    manager.priority = NotificationCompat.PRIORITY_DEFAULT
                }
                Priority.HIGH -> {
                    manager.priority = NotificationCompat.PRIORITY_HIGH
                }
                Priority.MAX -> {
                    manager.priority = NotificationCompat.PRIORITY_MAX
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channel?.importance = getImportance(priority)
            }
            return this
        }
    }

    /**
     * NONE 不重要的消息，任意情况下都不显示
     * MIN 不重要要的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
     * LOW 重要程度较低的消息,消息会出现，不过不会出现任何提示，无语音，无震动，状态栏无图标。手机设置中的表现为“静默通知”
     * DEFAULT 默认的重要程度,状态栏有图标，有声音提示
     * HIGH 重要程度比较高的消息,状态栏有图标，有声音提示，存在震动提示
     * MAX 非常重要的消息提示,状态栏有图标，有声音提示，存在震动提示
     */
    enum class Priority {
        NONE, MIN, LOW, DEFAULT, HIGH, MAX
    }

    /**
     * 通知可见情况
     * PRIVATE：显示基本信息，例如通知图标和内容标题，但隐藏通知的完整内容。
     * PUBLIC： 显示通知的完整内容。
     * SECRET：不在锁定屏幕上显示该通知的任何部分。
     */
    enum class Visibility{
        PUBLIC,SECRET,PRIVATE
    }
}