# fcmfix and no-root FCM reliability guide

The [fcmfix project](https://github.com/kooritea/fcmfix) uses LSPosed/Xposed hooks inside Android system services and Google Play Services. The techniques below show what root/Xposed changes and what any Android user can do without root to reduce FCM message-notification failures.

## Setup guide for any FCM app

These steps apply to any application that depends on FCM, not only FCM Status:

1. **Allow notifications.** Grant the app notification permission and ensure its notification channel is enabled.
2. **Remove battery restrictions.** Open **App info → Battery** and choose **Unrestricted** or **Don't optimize**.
3. **Allow background activity.** Enable **Allow background usage** or the equivalent setting provided by the device.
4. **Enable Autostart.** On vivo, Xiaomi, ColorOS, and similar ROMs, allow **Autostart**, **Background start**, or the vendor's equivalent.
5. **Lock the app in recents.** If the device provides a lock icon in recent apps, lock the FCM-dependent app there. Do not use **Force stop** when testing FCM.
6. **Check the VPN.** Make sure the VPN allows both Google Play Services and the target app to use the network. Verify that `mtalk.google.com:5228` is reachable.
7. **Test Doze separately.** For a temporary diagnostic test, a computer connected through ADB can run `adb shell dumpsys deviceidle disable`. Restore normal behavior with `adb shell dumpsys deviceidle enable`. This is not a permanent app setting.

These settings cannot guarantee delivery when the phone has no network, DNS is failing, a VPN blocks Google services, or the device manufacturer forcibly stops processes. They remove common background restrictions that are under the user's control.

## Comparison with fcmfix

| fcmfix technique | No-root guidance or equivalent | Limitation without root |
| --- | --- | --- |
| Add `FLAG_INCLUDE_STOPPED_PACKAGES` to FCM broadcasts | Do not force-stop the app; enable battery, background, Autostart, and recent-app protections | An ordinary app cannot modify Google Play Services or system broadcast flags for another app |
| Override OEM autostart blockers on vivo, MIUI, ColorOS, and similar ROMs | Manually allow Autostart or Background start in the vendor settings | OEM private methods can only be overridden with system privileges or Xposed |
| Hook GMS `HeartbeatChimeraAlarm` and change its internal timer | Keep the app unrestricted and use a user-space heartbeat/diagnostic app to test the FCM endpoint | An app cannot change GMS's private heartbeat timer or internal wakelock bookkeeping |
| Automatically send `GCM_RECONNECT` when GMS's internal timer expires | Check `mtalk.google.com:5228` and reconnect or retry after a failed probe when the app is running | A user-space reconnect request cannot restore connectivity when the VPN, DNS, or network itself is unavailable |
| Remove OEM notification/background restrictions | Grant notification permission and disable vendor battery/background restrictions manually | The app cannot remove restrictions imposed by the ROM |

## Summary

fcmfix changes Android and GMS behavior from inside the system. Without root, users cannot reproduce those hooks, but they can substantially reduce FCM failures by allowing notifications, removing battery restrictions, enabling Autostart/background activity, locking the app in recents, checking VPN access, and using Doze controls only for diagnosis. FCM Status helps verify the actual `mtalk.google.com:5228` connection and provides guidance for these settings.
