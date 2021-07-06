package com.xraph.plugin.flutter_unity_widget

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import com.unity3d.player.IUnityPlayerLifecycleEvents
import com.unity3d.player.UnityPlayer
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.jvm.javaClass;

class UnityPlayerUtils {

    companion object {
        private const val LOG_TAG = "UnityPlayerUtils"

        var activity: Activity? = null
        var options: FlutterUnityWidgetOptions = FlutterUnityWidgetOptions()
        var unityPlayer: UnityPlayer? = null

        var isUnityReady: Boolean = false
        var isUnityPaused: Boolean = false
        var isUnityLoaded: Boolean = false
        var isUnityInBackground: Boolean = false

        private val mUnityEventListeners = CopyOnWriteArraySet<UnityEventListener>()

        /**
         * Create a new unity player with callback
         */
        fun createPlayer(activity: Activity, ule: IUnityPlayerLifecycleEvents, reInitialize: Boolean, callback: OnCreateUnityViewCallback?) {
            if (unityPlayer != null && !reInitialize) {
                callback?.onReady()
                return
            }

            try {
                Handler(Looper.getMainLooper()).post {
                    if (!reInitialize) {

                        activity.window.setFormat(PixelFormat.RGBA_8888)
                        // activity.window.setFormat(PixelFormat.TRANSPARENT)
                        unityPlayer = UnityPlayer(activity, ule)
                        val f = UnityPlayer::class.java.getDeclaredField("mGlView")
                        f.isAccessible = true
                        val v = f.get(unityPlayer) as SurfaceView
                        unityPlayer!!.removeView(v)
                        // v.setZOrderMediaOverlay(true)
                        // v.getHolder().setFormat(PixelFormat.TRANSPARENT)
                        // v.setZOrderOnTop(false)
                        // unityPlayer!!.addView(v)
                        val updateGLDisplay = UnityPlayer::class.java.getDeclaredMethod("updateGLDisplay", Int::class.java, Surface::class.java)
                        updateGLDisplay.setAccessible(true)
                        val sendSurfaceChangedEvent = UnityPlayer::class.java.getDeclaredMethod("sendSurfaceChangedEvent")
                        sendSurfaceChangedEvent.setAccessible(true)
                        val view = TextureView(activity);
                        view.setOpaque(false)
                        view.setSurfaceTextureListener(object : TextureView.SurfaceTextureListener{
                            override fun onSurfaceTextureAvailable(surface:SurfaceTexture,  width:Int, height:Int) {
                                unityPlayer!!.displayChanged(0, Surface(surface))
                                updateGLDisplay.invoke(unityPlayer, 0, Surface(surface));
                            }

                            override fun onSurfaceTextureDestroyed(surface:SurfaceTexture ) : Boolean {
                                Log.e(LOG_TAG, "surface texture destroyed")
                                updateGLDisplay.invoke(unityPlayer, 0, null);
                                return true
                             }

                            override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture , width:Int, height:Int) {
                                Log.e(LOG_TAG, "surface texture size changed")
                                // updateGLDisplay.invoke(unityPlayer, 0, Surface(surface));
                                unityPlayer!!.removeView(view)
                                unityPlayer!!.addViewToPlayer(view, true)
                                // sendSurfaceChangedEvent.invoke(unityPlayer);
                            }

                            override fun onSurfaceTextureUpdated(surface:SurfaceTexture) {
                                // Log.e(LOG_TAG, "surface texture updated ")
                                updateGLDisplay.invoke(unityPlayer, 0, Surface(surface));
                                sendSurfaceChangedEvent.invoke(unityPlayer);
                            }
                        });
                        unityPlayer!!.addViewToPlayer(view, true)
                        
                    }

                    try {
                        if (!reInitialize) {
                            // wait a moment. fix unity cannot start when startup.
                            Thread.sleep(700)
                        }
                    } catch (e: Exception) {
                    }

                    // start unity
                    if (!reInitialize) {
                        addUnityViewToBackground(activity)
                        unityPlayer!!.windowFocusChanged(true)
                        unityPlayer!!.requestFocus()
                        unityPlayer!!.resume()

                        // restore window layout
                        if (!options.fullscreenEnabled) {
                            activity.window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                                activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                            }
                        }
                    }

                    isUnityReady = true
                    isUnityLoaded = true

                    callback?.onReady()
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, e.toString())
            }
        }

        fun postMessage(gameObject: String, methodName: String, message: String) {
            if (!isUnityReady) {
                Log.e("UnityPlayerUtils", "Not ready!")
                return
            }
            UnityPlayer.UnitySendMessage(gameObject, methodName, message)
        }

        fun pause() {
            if (unityPlayer != null && isUnityLoaded) {
                unityPlayer!!.pause()
                isUnityPaused = true
            }
        }

        fun resume() {
            if (unityPlayer != null) {
                unityPlayer!!.resume()
                isUnityPaused = false
            }
        }

        fun unload() {
            if (unityPlayer != null) {
                unityPlayer!!.unload()
                isUnityLoaded = false
            }
        }

        fun moveToBackground() {
            if (unityPlayer != null) {
                isUnityInBackground = true
            }
        }

        fun quitPlayer() {
            try {
                if (unityPlayer != null) {
                    unityPlayer!!.quit()
                    isUnityLoaded = false
                    isUnityReady = false
                }
            } catch (e: Error) {
                e.message?.let { Log.e(LOG_TAG, it) }
            }
        }

        /**
         * Invoke by unity C#
         */
        @JvmStatic
        fun onUnitySceneLoaded(name: String, buildIndex: Int, isLoaded: Boolean, isValid: Boolean) {
            Log.e(LOG_TAG, "Unity scenel oaded")

            for (listener in mUnityEventListeners) {
                try {
                    listener.onSceneLoaded(name, buildIndex, isLoaded, isValid)
                } catch (e: Exception) {
                    e.message?.let { Log.e(LOG_TAG, it) }
                }
            }
        }

        /**
         * Invoke by unity C#
         */
        @JvmStatic
        fun onUnityMessage(message: String) {
            Log.e(LOG_TAG, "UNITY Message " + message)

            for (listener in mUnityEventListeners) {
                try {
                    listener.onMessage(message)
                } catch (e: Exception) {
                    e.message?.let { Log.e(LOG_TAG, it) }
                }
            }
        }

        fun addUnityEventListener(listener: UnityEventListener) {
            mUnityEventListeners.add(listener)
        }

        fun removeUnityEventListener(listener: UnityEventListener) {
            mUnityEventListeners.remove(listener)
        }

        fun addUnityViewToBackground(activity: Activity) {
            if (unityPlayer == null) {
                return
            }
            if (unityPlayer!!.parent != null) {
                (unityPlayer!!.parent as ViewGroup).removeView(unityPlayer)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                unityPlayer!!.z = -1f
            }
            val layoutParams = ViewGroup.LayoutParams(1, 1)
            activity.addContentView(unityPlayer, layoutParams)
            isUnityInBackground = true
        }

        fun restoreUnityViewFromBackground(activity: Activity) {
            if (unityPlayer == null) {
                return
            }

            if (unityPlayer!!.parent != null) {
                (unityPlayer!!.parent as ViewGroup).addView(unityPlayer)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                unityPlayer!!.z = 1f
            }

            val layoutParams = ViewGroup.LayoutParams(1, 1)
            activity.addContentView(unityPlayer, layoutParams)
            isUnityInBackground = false
        }

        fun addUnityViewToGroup(group: ViewGroup) {
            if (unityPlayer == null) {
                return
            }

            if (unityPlayer!!.parent != null) {
                (unityPlayer!!.parent as ViewGroup).removeView(unityPlayer)
            }

            val layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            group.addView(unityPlayer, 0, layoutParams)

            unityPlayer!!.windowFocusChanged(true)
            unityPlayer!!.requestFocus()
            unityPlayer!!.resume()
        }
    }
}