* fix the TsurugiDB started through Belayer, which stopped together with the systemd service of Belayer (stop, restart or crash). Registered with `register_service.sh`, the TsurugiDB runs in a service of its own.
* fix `--prefix` of `install.sh`, which took any argument as the install directory.
