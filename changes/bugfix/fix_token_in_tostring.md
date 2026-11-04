* stop writing the authentication token to the DEBUG log. Jobs and request parameters (e.g. of the dump, load, backup, restore and session APIs) were logged with `toString()`, which included the token, and so was the login result with the issued access and refresh tokens.
    * Note: `com.tsurugidb` logs at DEBUG unless `BELAYER_LOG_LEVEL` is set.
