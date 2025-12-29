package com.github.codeworkscreativehub.mlauncher.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.Xml
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.R
import org.xmlpull.v1.XmlPullParser
import java.io.FileNotFoundException

enum class IconCacheTarget {
    APP_LIST,
    HOME
}

object IconPackHelper {
    private val appListIconCache = mutableMapOf<String, Drawable>()
    private val homeIconCache = mutableMapOf<String, Drawable>()
    private val nameCache = mutableMapOf<String, String>()

    @SuppressLint("DiscouragedApi")
    fun preloadIcons(
        context: Context,
        iconPackPackage: String,
        target: IconCacheTarget
    ) {
        try {
            when (target) {
                IconCacheTarget.APP_LIST -> appListIconCache.clear()
                IconCacheTarget.HOME -> homeIconCache.clear()
            }

            AppLogger.d("IconPackLoader", "Starting preload for: $iconPackPackage, target=$target")

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val allPackageNames = mutableSetOf<String>()
            userManager.userProfiles.forEach { user ->
                try {
                    val apps = launcherApps.getActivityList(null, user)
                    allPackageNames.addAll(apps.map { it.applicationInfo.packageName })
                } catch (e: Exception) {
                    AppLogger.e("IconPackLoader", "Failed to get apps for user $user", e)
                }
            }

            val iconPackContext = context.createPackageContext(iconPackPackage, 0)
            val assetManager = iconPackContext.assets

            val possibleAssetNames = listOf("appfilter.xml", "appmap.xml", "drawable.xml")

            val inputStream = possibleAssetNames
                .firstNotNullOfOrNull { fileName ->
                    try {
                        AppLogger.d("IconPackLoader", "Trying to open $fileName")
                        assetManager.open(fileName)
                    } catch (_: Exception) {
                        null
                    }
                } ?: throw FileNotFoundException("No valid XML asset found in $iconPackPackage")

            val parser = Xml.newPullParser()
            parser.setInput(inputStream, null)

            val regex = Regex("""ComponentInfo\{([^/]+)(/[^}]*)?\}""")
            var eventType = parser.eventType
            var loadedCount = 0
            var skippedCount = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    var drawable = parser.getAttributeValue(null, "drawable")

                    val match = regex.find(component ?: "")
                    val pkgName = match?.groupValues?.get(1)

                    if (pkgName != null && drawable != null) {
                        if (!allPackageNames.contains(pkgName)) {
                            skippedCount++
                            eventType = parser.next()
                            continue
                        }

                        drawable = drawable.removePrefix("@drawable/").removePrefix("drawable/")
                        nameCache[pkgName] = drawable

                        val resId = iconPackContext.resources.getIdentifier(
                            drawable,
                            "drawable",
                            iconPackPackage
                        )
                        if (resId != 0) {
                            ResourcesCompat.getDrawable(
                                iconPackContext.resources,
                                resId,
                                iconPackContext.theme
                            )?.let {
                                when (target) {
                                    IconCacheTarget.APP_LIST -> appListIconCache[pkgName] = it
                                    IconCacheTarget.HOME -> homeIconCache[pkgName] = it
                                }
                                loadedCount++
                            } ?: AppLogger.w("IconPackLoader", "Drawable is null for $drawable (resId=$resId)")
                        } else {
                            skippedCount++
                        }
                    } else {
                        skippedCount++
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()

            AppLogger.d("IconPackLoader", "Preload finished. Loaded: $loadedCount, Skipped: $skippedCount")

        } catch (e: Exception) {
            AppLogger.e("IconPackLoader", "Error while preloading icon pack: ${e.message}", e)
        }
    }

    fun getCachedIcon(context: Context, packageName: String, target: IconCacheTarget): Drawable? {
        val cachedIcon = when (target) {
            IconCacheTarget.APP_LIST -> appListIconCache[packageName]
            IconCacheTarget.HOME -> homeIconCache[packageName]
        }

        return cachedIcon ?: try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSafeAppIcon(
        context: Context,
        packageName: String,
        useIconPack: Boolean,
        iconPackTarget: IconCacheTarget
    ): Drawable {
        val pm = context.packageManager
        val icon = try {
            if (useIconPack) {
                getCachedIcon(context, packageName, iconPackTarget)
            } else {
                pm.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            AppLogger.e("IconHelper", "Failed to get icon for package: $packageName", e)
            null
        }

        return icon
            ?: ContextCompat.getDrawable(context, R.drawable.ic_default_app)
            ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            ?: run {
                AppLogger.w("IconHelper", "All fallback icons missing for $packageName; using transparent drawable")
                Color.TRANSPARENT.toDrawable()
            }
    }
}
