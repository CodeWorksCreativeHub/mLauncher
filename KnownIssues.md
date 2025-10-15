# Known Issues

This document lists known issues in the project and possible workarounds.

## 1.10.3.2 - mLauncher [Release Notes](https://github.com/CodeWorksCreativeHub/mLauncher/releases/tag/1.10.3.2) / 1.10.3.3 - mLauncher [Release Notes](https://github.com/CodeWorksCreativeHub/mLauncher/releases/tag/1.10.3.3)

### ⚠️ Icon Pack Compatibility Issue

These versions have a known issue affecting icon pack functionality.

To avoid problems during the update:

1. **Before updating**, go to settings and **disable your icon pack**.
2. Update to the new version.
3. **After updating**, re-enable your icon pack.

If you skip this step, icons may not load or apply correctly.

We're aware of the issue and will aim to improve icon pack handling in future updates.

## 1.7.5 - mLauncher [Release Notes](https://github.com/CodeWorksCreativeHub/mLauncher/releases/tag/1.7.5)

### 📱 Issue: Icons and Usage Problems

- **Cause:** The usage prevents the icons from functioning properly. If you want the icons to work, avoid using the "usage" feature. I'm unsure if I'll fix this issue.

## 1.7.4 - mLauncher [Release Notes](https://github.com/CodeWorksCreativeHub/mLauncher/releases/tag/1.7.4)

### 🛠️ Issue: `isManagedProfile()` Requires API 30+

- **Cause:** The method `UserManager.isManagedProfile(userHandle)` requires API 30 or higher, but the minimum SDK version of this project is lower.
- **Fix Coming Soon:** Use `val isWorkProfile = appListItem.user != android.os.Process.myUserHandle() && !isPrivateSpace` as a workaround for API
  versions below 30. For an example, see the implementation in [
  `AppDrawerAdapter.kt`](./app/src/main/java/com/github/droidworksstudio/mlauncher/ui/AppDrawerAdapter.kt).

## 1.7.3 - mLauncher [Release Notes](https://github.com/CodeWorksCreativeHub/mLauncher/releases/tag/1.7.3)

### 🚨 Issue: `NoSuchMethodError` for `getLauncherUserInfo()`

- **Cause:** The method `getLauncherUserInfo()` is missing in some Android versions.
- **Fix Coming Soon:** Use `UserManager.isManagedProfile()` with a compatibility check.
  See [this discussion](https://developer.android.com/reference/android/os/UserManager#isManagedProfile()).

### 🛠️ Issue: `isManagedProfile()` Requires API 30+

- **Problem:** `UserManager.isManagedProfile(userHandle)` requires API 30, but our `minSdkVersion` is lower.
- **Fix Coming Soon:** Use reflection for API < 30. See the implementation in [
  `AppDrawerAdapter.kt`](./app/src/main/java/com/github/droidworksstudio/mlauncher/ui/AppDrawerAdapter.kt).

### 📱 Issue: 3-Button Navigation Doesn't Leave Space at the Bottom of Settings

- **Cause:** When 3-button navigation is enabled, the bottom area of the settings screen is not properly adjusted, leading to content being cut off or
  obscured.
- **Solution:** Use **gesture navigation** as a workaround, which doesn't have the same issue with screen space. To enable gesture navigation, go to *
  *Settings > System > Gestures > System navigation** and choose **Gesture navigation**.
- **Fix Coming Soon:** A fix is in progress, and we will add a spacer to the bottom of the pages to resolve this issue.

---

#### Reporting Issues

If you encounter additional issues, please open a [GitHub Issue](https://github.com/CodeWorksCreativeHub/mLauncher/issues) or contribute a fix.
