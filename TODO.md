# Pending improvements

## Keep-alive reliability

- [ ] Improve boot recovery: have `BOOT_COMPLETED` schedule the heartbeat alarm directly before attempting to start the foreground service, so the app can resume after reboot without being opened again.
- [ ] Do not cancel the heartbeat alarm from `HeartbeatService.onDestroy()`; cancel it only when the user explicitly stops keep-alive.
- [ ] Add conservative connection-loss detection before each heartbeat.
- [ ] After consecutive probe failures, send one `GCM_RECONNECT` attempt and avoid repeated reconnects while the VPN or internet is unavailable.
- [ ] Handle VPN and network changes, including rescheduling and probing when connectivity returns.

## Diagnostics and UI

- [ ] Display last outage time, recovery time, outage duration, consecutive probe failures, and last reconnect attempt.
- [ ] Clearly distinguish between a heartbeat broadcast being sent, the FCM server being reachable, and the Google Play Services connection being verified.
- [ ] Review selected font sizes and reduce oversized verdict/button text without shrinking all typography.
- [ ] Add clearer OEM instructions for autostart, battery restrictions, locked apps, and stopped-app behavior.

## Testing

- [ ] Test reboot recovery without reopening the app.
- [ ] Test swipe-away, vivo service killing, Force Stop, VPN loss, and VPN recovery separately.
- [ ] Confirm that restarting keep-alive leaves only one scheduled alarm.
