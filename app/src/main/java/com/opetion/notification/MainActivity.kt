package com.opetion.notification

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setBtn(R.id.vBtnNoti,NotificationHelper.Priority.NONE)
        setBtn(R.id.vBtnNotiThree,NotificationHelper.Priority.LOW)
        setBtn(R.id.vBtnNotiFour,NotificationHelper.Priority.MAX)
        findViewById<Button>(R.id.vBtnNoti).setOnClickListener {
            val fullScreenIntent = Intent(this, MainActivity2::class.java)
            val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

            NotificationHelper.NotificationHelperBuilder(this, "测试", "110",12)
                .setContentTitle("测试")
                .enableVibration(false)
                .setContentText("他们发你的健康方便大家不妨发动机发表的机会妇女大家看法吧发动机报复的机会发动机和发红包")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setCustomView(R.layout.notification_custom_small,View.NO_ID)
                .setContentIntent(fullScreenPendingIntent)
                .setShowBadge(true)
                .setVisibility(NotificationHelper.Visibility.PUBLIC)
                .show()
        }
        findViewById<Button>(R.id.vBtnNotiTwo).setOnClickListener {
            NotificationHelper.sendToSetting(this,"110")
        }
        findViewById<Button>(R.id.vBtnNotiThree).setOnClickListener {
            NotificationHelper.NotificationHelperBuilder(this, "测试", "110",12).build()
            val hadPermission = NotificationHelper.hadImportancePermission(this,"110")
            Log.i("日志","是否存横幅在权限：${hadPermission}")
        }
        findViewById<Button>(R.id.vBtnNotiFour).setOnClickListener {
            val hadPermission = NotificationHelper.hadNotificationPermission(this)
            Log.i("日志","是否存在权限：${hadPermission}")
        }
    }

    fun setBtn(id:Int,priority: NotificationHelper.Priority){
        findViewById<Button>(id).setOnClickListener {

        }
    }
}