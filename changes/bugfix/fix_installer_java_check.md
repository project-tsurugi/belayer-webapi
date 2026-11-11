* fix `install.sh`, which went on installing when java was not found or its version could not be read. It now also exits with 1 when it stops on an error (`exit -1` gave 2 under `sh`).
