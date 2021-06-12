package com.opetion.notification

import android.os.Build




/**
 * @author:libowu
 * @Date:2021/5/28
 * @Description:
 */
object SystemUtil {

    private fun getSystemStr():String{
        val manufacturer = Build.MANUFACTURER
        return manufacturer
    }

    /**
     * 是否是华为设备
     */
    fun isHuaWei():Boolean{
        if (getSystemStr().equals("huawei",true)){
            return true
        }
        return false
    }

    fun isGoogle():Boolean{
        if (getSystemStr().equals("google",true)){
            return true
        }
        return false
    }

    /**
     * 是否时vivo设备
     */
    fun isVivo():Boolean{
        if (getSystemStr().equals("vivo",true)){
            return true
        }
        return false
    }

    /**
     * 是否是oppo设备
     */
    fun isOppo():Boolean{
        if (getSystemStr().equals("oppo",true)){
            return true
        }
        return false
    }

    fun isXiaomi():Boolean{
        if (getSystemStr().equals("xiaomi",true)){
            return true
        }
        return false
    }
}