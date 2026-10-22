# Ops

Data fixes, config changes and manual steps: date, exact command, why.
- 2026-10-22: `adb -s 192.168.10.64:5555 shell pm disable-user --user 0 com.google.android.tvlauncher` on the Mi Box test device; Projectivy (`com.spocky.projengmenu`) is home because the stock launcher outranks `set-home-activity`. Undo: `pm enable com.google.android.tvlauncher`.
- 2026-10-22: reverted the above: `pm enable com.google.android.tvlauncher` and `adb uninstall com.spocky.projengmenu`; user found Projectivy unsuitable. Stock launcher is home again.
