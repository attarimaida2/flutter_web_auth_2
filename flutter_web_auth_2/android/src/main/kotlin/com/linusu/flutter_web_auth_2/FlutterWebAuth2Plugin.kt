package com.linusu.flutter_web_auth_2

import android.app.Activity
import android.content.Intent
import android.net.Uri

import androidx.browser.customtabs.CustomTabsIntent

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.lang.ref.WeakReference

class FlutterWebAuth2Plugin: MethodCallHandler, FlutterPlugin,
  ActivityAware {
  private var channel: MethodChannel? = null

  companion object {
    val callbacks = mutableMapOf<String, Result>()
    internal var activity: WeakReference<Activity>? = null

    @JvmStatic
    fun registerWith(registrar: Registrar) {
        val plugin = FlutterWebAuth2Plugin()
        activity = WeakReference(registrar.activity())
        plugin.initChannel(registrar.messenger())
    }

    fun maybeCloseCustomTab() {
        activity?.get()?.let {
            val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
            intent?.setPackage(null)
            intent?.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            it.startActivity(intent)
        }
    }
  }

  fun initChannel(messenger: BinaryMessenger) {
      channel = MethodChannel(messenger, "flutter_web_auth_2")
      channel?.setMethodCallHandler(this)
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) =
      initChannel(binding.binaryMessenger)

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      activity = null
      channel = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activity = WeakReference(binding.activity)
  }

  override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) =
      onAttachedToActivity(binding)

  override fun onDetachedFromActivity() {
      activity = null
  }

  override fun onMethodCall(call: MethodCall, resultCallback: Result) {
    when (call.method) {
        "authenticate" -> {
          val activity = FlutterWebAuth2Plugin.activity?.get()

          if (activity == null) {
            resultCallback.error("MISSING_ACTIVITY", "Engine is not attached to activity. Cannot open custom tab", null)
            return
          }

          val url = Uri.parse(call.argument("url"))
          val callbackUrlScheme = call.argument<String>("callbackUrlScheme")!!
          val options = call.argument<Map<String, Any>>("options")!!

          callbacks[callbackUrlScheme] = resultCallback

          val intent = CustomTabsIntent.Builder().build()

          intent.intent.addFlags(options["intentFlags"] as Int)

          intent.launchUrl(activity, url)
        }
        "cleanUpDanglingCalls" -> {
          callbacks.forEach{ (_, danglingResultCallback) ->
              danglingResultCallback.error("CANCELED", "User canceled login", null)
          }
          callbacks.clear()
          resultCallback.success(null)
        }
        else -> resultCallback.notImplemented()
    }
  }
}
