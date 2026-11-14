* split the installation into three steps: `apt-install.sh` (root; installs Java, `jq` and Python 3 when missing), `install.sh` (the user that runs the TsurugiDB) and `systemd/register_service.sh` (root; only to run Belayer as a systemd service).
    * `install.sh` no longer needs root and registers no service. Run it as the user that runs the TsurugiDB; a user that cannot write to `$TSURUGI_HOME/bin` gets an error. `systemd/add_service.sh` is removed.
    * the install directory defaults to the directory that holds the TsurugiDB (`/usr/lib` for `/usr/lib/tsurugi`, as before), and the storage root to `<install dir>/storage`, which `install.sh` creates and writes to `config/tsurugi-belayer.conf`.
    * the unit of Belayer runs as the owner of the install directory (`User=` / `Group=`; root before). `register_service.sh` stops when the TsurugiDB belongs to another user.
    * a re-install of the same version keeps the files already in `config/`.
