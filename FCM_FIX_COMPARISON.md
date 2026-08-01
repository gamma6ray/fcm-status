# fcmfix and FCM Status comparison

The [fcmfix project](https://github.com/kooritea/fcmfix) uses LSPosed/Xposed hooks inside Android system services and Google Play Services. FCM Status uses only public app-level capabilities, so it improves connection reliability but cannot bypass every system restriction.

| fcmfix technique | What FCM Status can do without root | Limitation |
| --- | --- | --- |
| Add `FLAG_INCLUDE_STOPPED_PACKAGES` to FCM broadcasts | Keep the app configured correctly and provide background-access guidance | An ordinary app cannot modify Google Play Services or the system broadcast flags for another app |
| Override OEM autostart blockers on vivo, MIUI, ColorOS, and similar ROMs | Open App info and explain the correct Autostart, battery, and recent-app settings | OEM private methods can only be overridden with system privileges or Xposed |
| Hook GMS `HeartbeatChimeraAlarm` and change its internal timer | Schedule an external `MCS_HEARTBEAT` broadcast with an alarm that can wake from Doze | The app cannot change GMS's private heartbeat timer or its internal wakelock bookkeeping |
| Automatically send `GCM_RECONNECT` when GMS's internal timer expires | Test `mtalk.google.com:5228` before or after a heartbeat and detect an outage | A user-space reconnect request cannot restore connectivity when the VPN, DNS, or network itself is unavailable |
| Remove OEM notification/background restrictions | Use an ongoing notification and guide the user through the required settings | The app cannot remove restrictions imposed by the ROM |

## Summary

fcmfix can change Android and GMS behavior from inside the system. FCM Status works around the same conditions from outside the system: it schedules heartbeats, tests the real FCM endpoint, reports the result, and guides the user through settings that the app is not permitted to change itself.
